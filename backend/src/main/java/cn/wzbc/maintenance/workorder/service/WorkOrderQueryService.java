package cn.wzbc.maintenance.workorder.service;
import cn.wzbc.maintenance.workorder.entity.*;
import cn.wzbc.maintenance.workorder.mapper.WorkOrderMapper;
import cn.wzbc.maintenance.workorder.vo.WorkOrderDetailVo;
import cn.wzbc.maintenance.workorder.vo.WorkOrderDetailVo.*;
import cn.wzbc.maintenance.repairrequest.service.RepairRequestService;
import cn.wzbc.maintenance.repairrequest.mapper.RepairRequestMapper;
import cn.wzbc.maintenance.repairrequest.vo.RepairRequestDetailVo.SubmissionVo;
import cn.wzbc.maintenance.repairrequest.vo.RepairRequestDetailVo.EquipmentReferenceVo;
import cn.wzbc.maintenance.attachment.entity.AttachmentEntity;
import cn.wzbc.maintenance.attachment.mapper.AttachmentMapper;
import cn.wzbc.maintenance.attachment.vo.AttachmentVo;
import cn.wzbc.maintenance.equipment.mapper.EquipmentMapper;
import cn.wzbc.maintenance.account.vo.CurrentUserVo;
import cn.wzbc.maintenance.common.*;
import cn.wzbc.maintenance.security.*;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static cn.wzbc.maintenance.common.OperationResultService.*;

@Service
public class WorkOrderQueryService {
    private final WorkOrderMapper orders; private final BusinessAccessPolicy access; private final RepairRequestMapper requests; private final RepairRequestService materials;
    private final EquipmentMapper equipment; private final AttachmentMapper files; private final Clock clock;
    public WorkOrderQueryService(WorkOrderMapper orders,BusinessAccessPolicy access,RepairRequestMapper requests,RepairRequestService materials,EquipmentMapper equipment,AttachmentMapper files,Clock clock) { this.orders=orders; this.access=access; this.requests=requests; this.materials=materials; this.equipment=equipment; this.files=files; this.clock=clock; }
    public record NavigationCountVo(String userId,CurrentUser.Role role,long count) {}
    @Transactional(readOnly=true)
    public NavigationCountVo navigationCount(CurrentUser actor) {
        access.requireUser(actor,Set.of(CurrentUser.Role.ADMIN,CurrentUser.Role.REPORTER,CurrentUser.Role.TECHNICIAN),false);
        var query=access.orderQuery(actor,"CURRENT");
        if(actor.role()==CurrentUser.Role.TECHNICIAN) query.in("status",List.of("PENDING_ACCEPT","IN_PROGRESS"));
        else if(actor.role()==CurrentUser.Role.REPORTER) query.eq("status","PENDING_ACCEPTANCE");
        return new NavigationCountVo(Long.toString(actor.id()),actor.role(),orders.selectCount(query));
    }
    @Transactional(readOnly=true)
    public PageResult<SummaryVo> list(CurrentUser actor,int page,int size,String keyword,String status,String scope,String location,String technician,Long equipmentId) {
        page(page,size,keyword); if(!Set.of("CURRENT","HISTORY","ALL").contains(scope)) throw error(400,"INVALID_QUERY","工单范围不合法");
        var q=access.orderQuery(actor,scope);
        if(status!=null && !status.isBlank()) { if(!Set.of("PENDING_ACCEPT","REASSIGN_PENDING","IN_PROGRESS","PENDING_ACCEPTANCE","COMPLETED").contains(status)) throw error(400,"INVALID_QUERY","工单状态不合法"); q.eq("status",status); }
        if(location!=null && !location.isBlank()) q.eq("location_id",id(location));
        if(equipmentId!=null) q.eq("equipment_id",equipmentId);
        if(technician!=null && !technician.isBlank()) { long target=id(technician); if(actor.role()!=CurrentUser.Role.ADMIN && (actor.role()!=CurrentUser.Role.TECHNICIAN || target!=actor.id())) throw error(403,"FORBIDDEN","不能更改本人任务范围"); if(actor.role()==CurrentUser.Role.ADMIN) q.and(p -> p.eq("assigned_technician_id",target).or().eq("accepted_technician_id",target).or().apply("EXISTS(SELECT 1 FROM work_order_assignment a WHERE a.work_order_id=work_order.id AND a.technician_id={0})",target)); }
        if(keyword!=null && !keyword.isBlank()) q.and(p -> {
            p.apply("CONVERT(work_order_no USING utf8mb4) LIKE {0}","%"+keyword+"%").or().apply("EXISTS(SELECT 1 FROM repair_request r WHERE r.id=work_order.request_id AND (CONVERT(r.request_no USING utf8mb4) LIKE {0} OR r.equipment_name LIKE {0}))","%"+keyword+"%");
            if(actor.role()==CurrentUser.Role.ADMIN) p.or().apply("EXISTS(SELECT 1 FROM work_order_assignment a JOIN user_account u ON u.id=a.technician_id WHERE a.work_order_id=work_order.id AND (u.name LIKE {0} OR u.login_name LIKE {0}))","%"+keyword+"%");
        });
        long count=orders.selectCount(q);
        if(actor.role()==CurrentUser.Role.REPORTER) q.orderByAsc("CASE WHEN status='PENDING_ACCEPTANCE' THEN 0 ELSE 1 END");
        if(actor.role()==CurrentUser.Role.ADMIN) q.orderByAsc("CASE WHEN status IN ('PENDING_ACCEPT','REASSIGN_PENDING') THEN 0 ELSE 1 END");
        if(actor.role()==CurrentUser.Role.TECHNICIAN) q.orderByAsc("CASE WHEN status='PENDING_ACCEPT' AND assigned_technician_id="+actor.id()+" THEN 0 WHEN status='IN_PROGRESS' AND current_round>1 AND accepted_technician_id="+actor.id()+" THEN 1 WHEN status='IN_PROGRESS' AND accepted_technician_id="+actor.id()+" THEN 2 ELSE 3 END");
        q.orderByDesc("created_at","id").last("LIMIT "+size+" OFFSET "+((long)(page-1)*size));
        return new PageResult<>(orders.selectList(q).stream().map(o -> summary(o,actor)).toList(),count,page,size);
    }
    public SummaryVo summary(WorkOrderEntity o,CurrentUser actor) {
        String scope=access.historyScope(actor,o); if(scope==null) throw error(403,"RECORD_FORBIDDEN","没有查看该工单的权限"); boolean full=scope.equals("FULL"); var eq=o.equipmentId==null?null:equipment.selectById(o.equipmentId); var request=requests.selectById(o.requestId);
        var reference=eq==null?null:new EquipmentReferenceVo(eq.id.toString(),eq.equipmentNo,eq.name,eq.locationId==null?null:eq.locationId.toString());
        return new SummaryVo(o.id.toString(),o.workOrderNo,o.requestId.toString(),request.requestNo,request.equipmentName,reference,o.status,o.locationMode,materials.location(o.locationId),o.locationNameSnapshot,CurrentUserVo.instant(o.createdAt),full&&o.assignedTechnicianId!=null?materials.account(o.assignedTechnicianId):null,full&&o.acceptedTechnicianId!=null?materials.account(o.acceptedTechnicianId):null,full?o.version:null,full?o.currentRound:null,full?(o.status.equals("IN_PROGRESS")&&o.currentRound>1):null,actions(o,actor,scope),scope);
    }
    private List<String> actions(WorkOrderEntity o,CurrentUser actor,String scope) {
        if(!scope.equals("FULL")) return List.of();
        if(actor.role()==CurrentUser.Role.ADMIN && Set.of("PENDING_ACCEPT","REASSIGN_PENDING").contains(o.status)) return List.of("ASSIGN");
        if(actor.role()==CurrentUser.Role.TECHNICIAN && actor.reviewStatus()==CurrentUser.ReviewStatus.APPROVED) {
            if(o.status.equals("PENDING_ACCEPT") && Objects.equals(o.assignedTechnicianId,actor.id())) return List.of("ACCEPT","REFUSE");
            if(o.status.equals("IN_PROGRESS") && Objects.equals(o.acceptedTechnicianId,actor.id())) return List.of("PROGRESS","SUBMIT_RESULT");
        }
        if(actor.role()==CurrentUser.Role.REPORTER && o.status.equals("PENDING_ACCEPTANCE") && Objects.equals(o.reporterId,actor.id())) return List.of("ACCEPTANCE");
        return List.of();
    }
    @Transactional(readOnly=true)
    public WorkOrderDetailVo detail(CurrentUser actor,long id) {
        access.requireUser(actor,Set.of(CurrentUser.Role.ADMIN,CurrentUser.Role.REPORTER,CurrentUser.Role.TECHNICIAN),false); var o=orders.selectById(id); if(o==null) throw error(404,"RESOURCE_NOT_FOUND","工单不存在");
        var s=summary(o,actor); boolean full=s.historyScope().equals("FULL"); var visible=orders.assignments(id).stream().filter(a -> full || a.technicianId==actor.id()).toList();
        var r=requests.selectById(o.requestId); long snapshot=full?r.currentSubmissionId:visible.getFirst().visibleSubmissionId;
        var submission=requests.submission(snapshot); if(submission==null || !submission.requestId.equals(o.requestId)) throw error(500,"INTERNAL_ERROR","原材料快照不可用");
        var raw=materials.submissionVo(submission); var prior=requests.submissions(o.requestId).stream().filter(p -> p.submissionNo<=submission.submissionNo).map(p -> p.id).toList();
        var images=files.selectList(new QueryWrapper<AttachmentEntity>().in("bound_submission_id",prior).eq("state","BOUND").orderByAsc("id")).stream().map(AttachmentVo::from).toList();
        var material=new SubmissionVo(raw.id(),raw.submissionNo(),raw.equipmentName(),raw.equipmentCategory(),raw.locationMode(),raw.location(),raw.locationNameSnapshot(),raw.addressText(),raw.description(),raw.contactName(),raw.contactPhone(),images,raw.submittedAt());
        var assignments=visible.stream().map(a -> new AssignmentVo(a.id.toString(),a.assignmentNo,materials.account(a.technicianId),materials.account(a.assignedBy),a.visibleSubmissionId.toString(),CurrentUserVo.instant(a.assignedAt),CurrentUserVo.instant(a.endedAt),a.outcome,a.reason,CurrentUserVo.instant(a.decisionAt))).toList();
        var assignmentIds=visible.stream().map(a -> a.id).collect(java.util.stream.Collectors.toSet());
        var events=orders.events(id).stream().filter(e -> full || (e.assignmentId!=null && assignmentIds.contains(e.assignmentId) && Set.of("FIRST_ASSIGNED","ASSIGNED","REFUSED").contains(e.eventType)))
            .map(e -> new EventVo(e.id.toString(),e.eventType,e.fromStatus,e.toStatus,materials.account(e.actorUserId),e.reason,CurrentUserVo.instant(e.occurredAt))).toList();
        var rounds=full?orders.rounds(id).stream().map(this::round).toList():List.<RepairRoundVo>of();
        return new WorkOrderDetailVo(s.id(),s.workOrderNo(),s.requestId(),s.requestNo(),s.reportedEquipmentName(),s.equipment(),s.status(),s.locationMode(),s.location(),s.locationNameSnapshot(),s.createdAt(),s.assignedTechnician(),s.acceptedTechnician(),s.version(),s.round(),s.isRework(),s.allowedActions(),s.historyScope(),material,materials.account(o.reporterId),assignments,rounds,events,full?CurrentUserVo.instant(r.firstSubmittedAt):null,full?CurrentUserVo.instant(o.acceptedAt):null,full?CurrentUserVo.instant(o.completedAt):null,CurrentUserVo.instant(clock.instant()));
    }
    private RepairRoundVo round(RepairRoundEntity r) {
        var images=files.selectList(new QueryWrapper<AttachmentEntity>().eq("bound_round_id",r.id).eq("state","BOUND").orderByAsc("id")).stream().map(AttachmentVo::from).toList();
        var progress=orders.progress(r.id).stream().map(p -> new ProgressVo(p.id.toString(),r.roundNo,materials.account(p.technicianId),p.content,CurrentUserVo.instant(p.recordedAt))).toList();
        var acceptance=r.acceptanceAt==null?null:new AcceptanceVo(r.acceptancePassed,r.acceptanceReason,materials.account(r.acceptedBy),CurrentUserVo.instant(r.acceptanceAt));
        return new RepairRoundVo(r.id.toString(),r.roundNo,materials.account(r.technicianId),CurrentUserVo.instant(r.startedAt),CurrentUserVo.instant(r.submittedAt),r.resultText,images,acceptance,progress);
    }
}
