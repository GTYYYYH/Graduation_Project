package cn.wzbc.maintenance.workorder.service;

import cn.wzbc.maintenance.account.entity.UserEntity;
import cn.wzbc.maintenance.account.mapper.UserMapper;
import cn.wzbc.maintenance.account.vo.CurrentUserVo;
import cn.wzbc.maintenance.common.PageResult;
import cn.wzbc.maintenance.repairrequest.entity.RepairRequestEntity;
import cn.wzbc.maintenance.repairrequest.mapper.RepairRequestMapper;
import cn.wzbc.maintenance.security.*;
import cn.wzbc.maintenance.workorder.entity.*;
import cn.wzbc.maintenance.workorder.dto.AssignWorkOrderDto;
import cn.wzbc.maintenance.workorder.mapper.WorkOrderMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static cn.wzbc.maintenance.common.OperationResultService.*;

/** Qualified manual assignment for first review and B05 pre-acceptance changes. */
@Service
public class AssignmentService {
    private final UserMapper users; private final RepairRequestMapper requests; private final WorkOrderMapper orders;
    private final BusinessAccessPolicy access; private final Clock clock; private final WorkOrderMutationSupport work;
    public AssignmentService(UserMapper users,RepairRequestMapper requests,WorkOrderMapper orders,BusinessAccessPolicy access,Clock clock,WorkOrderMutationSupport work) { this.users=users; this.requests=requests; this.orders=orders; this.access=access; this.clock=clock; this.work=work; }
    public record RefusalReferenceVo(String assignmentId,String reason,String refusedAt) {}
    public record EligibleTechnicianVo(String id,String name,boolean enabled,String reviewStatus,List<RefusalReferenceVo> refusal,boolean eligible) {}
    public record AssignmentEligibleVo(PageResult<EligibleTechnicianVo> candidates,long eligibleTotal,String queriedAt) {}

    public static boolean eligible(UserEntity user) { return user!=null && user.role==CurrentUser.Role.TECHNICIAN && user.enabled && user.reviewStatus==CurrentUser.ReviewStatus.APPROVED; }
    public record CandidateContext(Long orderId,Long excludedTechnician) {}
    /** Shared with recommendations; final mutations still recheck under their own locks. */
    public CandidateContext candidateContext(CurrentUser actor,String context,String requestId,String workOrderId) {
        access.requireUser(actor,Set.of(CurrentUser.Role.ADMIN),false);
        Long orderId=null, excludedTechnician=null;
        if("FIRST_ASSIGN".equals(context)) {
            if(requestId==null || workOrderId!=null) throw error(400,"INVALID_QUERY","首次派单须指定待审核申请，不能同时指定工单");
            var request=requests.selectById(id(requestId)); if(request==null) throw error(404,"RESOURCE_NOT_FOUND","申请不存在");
            if(!request.status.equals("PENDING_REVIEW")) throw error(409,"STATE_CONFLICT","只有待审核申请可查询首次派单名单");
        } else if("CHANGE_BEFORE_ACCEPT".equals(context) || "REASSIGN".equals(context)) {
            if(workOrderId==null || requestId!=null) throw error(400,"INVALID_QUERY","重新分派须指定工单");
            orderId=id(workOrderId); var order=orders.selectById(orderId);
            if(order==null) throw error(404,"RESOURCE_NOT_FOUND","工单不存在");
            if(!order.status.equals(context.equals("REASSIGN")?"REASSIGN_PENDING":"PENDING_ACCEPT")) throw error(409,"STATE_CONFLICT","工单当前不可重新分派");
            if("CHANGE_BEFORE_ACCEPT".equals(context)) excludedTechnician=order.assignedTechnicianId;
        } else throw error(400,"INVALID_QUERY","派单场景不合法");
        return new CandidateContext(orderId,excludedTechnician);
    }
    public QueryWrapper<UserEntity> qualifiedQuery(CandidateContext context) {
        var q=new QueryWrapper<UserEntity>().eq("role",CurrentUser.Role.TECHNICIAN).eq("enabled",true).eq("review_status",CurrentUser.ReviewStatus.APPROVED);
        if(context.excludedTechnician()!=null) q.ne("id",context.excludedTechnician());
        return q;
    }
    @Transactional(readOnly=true)
    public AssignmentEligibleVo eligible(CurrentUser actor,String context,String requestId,String workOrderId,int page,int size,String keyword) {
        page(page,size,keyword);var target=candidateContext(actor,context,requestId,workOrderId);
        var q=qualifiedQuery(target);
        long total=users.selectCount(q);
        if(keyword!=null && !keyword.isBlank()) q.and(p -> p.like("name",keyword).or().like("login_name",keyword));
        long count=users.selectCount(q); q.orderByAsc("id").last("LIMIT "+size+" OFFSET "+((long)(page-1)*size));
        final Long selectedOrder=target.orderId();
        var candidates=users.selectList(q).stream().map(u -> {
            var refused=selectedOrder==null?null:orders.refusedBy(selectedOrder,u.id);
            var references=refused==null?List.<RefusalReferenceVo>of():List.of(new RefusalReferenceVo(refused.id.toString(),refused.reason,CurrentUserVo.instant(refused.decisionAt)));
            return new EligibleTechnicianVo(u.id.toString(),u.name,u.enabled,"APPROVED",references,eligible(u));
        }).toList();
        return new AssignmentEligibleVo(new PageResult<>(candidates,count,page,size),total,CurrentUserVo.instant(clock.instant()));
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public cn.wzbc.maintenance.common.OperationResultService.Receipt reassign(CurrentUser actor,long id,AssignWorkOrderDto dto) {
        uuid(dto.operationId()); long candidate=id(dto.technicianId()); String reason=text(dto.reason(),2000,"改派原因").trim();
        byte[] digest=work.operations.digest(Arrays.asList("ASSIGNED",id,dto.version(),candidate,reason,Boolean.TRUE.equals(dto.confirmPreviouslyRefused())));
        var locked=work.lock(actor,id,Set.of(CurrentUser.Role.ADMIN),candidate); var order=locked.order();
        var replay=work.replay(actor,"ASSIGNED",dto.operationId(),digest); if(replay!=null) return replay;
        locked.requireCurrentParticipants();
        version(order.version,dto.version());
        if(!Set.of("PENDING_ACCEPT","REASSIGN_PENDING").contains(order.status) || order.currentRound!=0) throw error(409,"STATE_CONFLICT","接单后不可改派");
        if(order.assignedTechnicianId!=null && order.assignedTechnicianId==candidate) throw error(409,"SAME_TECHNICIAN","不能改派给当前负责人");
        var selected=users.selectById(candidate); if(!eligible(selected)) throw error(409,"QUALIFICATION_CHANGED","所选维修人员资格已变化");
        if(orders.refusedBy(id,candidate)!=null && !Boolean.TRUE.equals(dto.confirmPreviouslyRefused())) throw error(409,"PREVIOUSLY_REFUSED_CONFIRMATION_REQUIRED","该维修人员曾拒单，请确认后重派");
        String before=order.status; var now=work.now(); AssignmentEntity previous=null;
        if(before.equals("PENDING_ACCEPT")) {
            previous=orders.lockAssignment(order.currentAssignmentId);
            if(previous==null || !previous.outcome.equals("PENDING")) throw error(409,"STATE_CONFLICT","原分派已变化");
            previous.endedAt=now; previous.decisionAt=now; previous.reason=reason; previous.outcome="REPLACED";
            if(orders.endAssignment(previous)!=1) throw error(409,"STATE_CONFLICT","原分派已变化");
        }
        var old=orders.assignments(id); int number=old.stream().mapToInt(a -> a.assignmentNo).max().orElse(0)+1;
        var request=requests.selectById(order.requestId); if(request==null || request.currentSubmissionId==null) throw error(409,"STATE_CONFLICT","原报修材料不可用");
        var assignment=new AssignmentEntity(); assignment.id=IdWorker.getId(); assignment.createdAt=now; assignment.workOrderId=id; assignment.assignmentNo=number; assignment.technicianId=candidate; assignment.assignedBy=actor.id(); assignment.visibleSubmissionId=request.currentSubmissionId; assignment.assignedAt=now; assignment.outcome="PENDING";
        orders.insertAssignment(assignment);
        work.change(order,before,"PENDING_ACCEPT",candidate,assignment.id,null,null,null,0,now);
        if(previous!=null) {
            var replaced=new WorkOrderEventEntity(); replaced.id=IdWorker.getId(); replaced.createdAt=now; replaced.workOrderId=id; replaced.actorUserId=actor.id(); replaced.assignmentId=previous.id;
            replaced.eventType="ASSIGNED"; replaced.fromStatus="PENDING_ACCEPT"; replaced.toStatus="PENDING_ACCEPT"; replaced.reason=reason; replaced.occurredAt=now;
            orders.insertEvent(replaced);
        }
        return work.event(order,actor,"ASSIGNED",before,"PENDING_ACCEPT",reason,assignment.id,null,null,dto.operationId(),digest,id,now);
    }

    /** Caller owns the user/equipment/request locks and commits this with the request decision. */
    @Transactional(propagation=Propagation.MANDATORY)
    public WorkOrderEntity first(RepairRequestEntity request,Long equipmentId,UserEntity technician,CurrentUser actor,Instant now) {
        if(!eligible(technician)) throw error(409,"QUALIFICATION_CHANGED","所选维修人员资格已变化，请重新选择");
        var order=new WorkOrderEntity(); order.id=IdWorker.getId(); order.createdAt=now; order.updatedAt=now; order.workOrderNo="WO-"+order.id;
        order.requestId=request.id; order.equipmentId=equipmentId; order.reporterId=request.reporterId; order.locationMode=request.locationMode; order.locationId=request.locationId; order.locationNameSnapshot=request.locationNameSnapshot;
        order.status="PENDING_ACCEPT"; order.assignedTechnicianId=technician.id;
        orders.insert(order);
        var assignment=new AssignmentEntity(); assignment.id=IdWorker.getId(); assignment.createdAt=now; assignment.workOrderId=order.id; assignment.assignmentNo=1; assignment.technicianId=technician.id; assignment.assignedBy=actor.id(); assignment.visibleSubmissionId=request.currentSubmissionId; assignment.assignedAt=now; assignment.outcome="PENDING";
        orders.insertAssignment(assignment);
        if(orders.update(null,new UpdateWrapper<WorkOrderEntity>().eq("id",order.id).eq("version",0).set("current_assignment_id",assignment.id))!=1) throw error(409,"VERSION_CONFLICT","工单已变化，请重新核对"); order.currentAssignmentId=assignment.id;
        var event=new WorkOrderEventEntity(); event.id=IdWorker.getId(); event.createdAt=now; event.workOrderId=order.id; event.actorUserId=actor.id(); event.assignmentId=assignment.id; event.eventType="FIRST_ASSIGNED"; event.toStatus=order.status; event.occurredAt=now;
        orders.insertEvent(event); return order;
    }
}
