package cn.wzbc.maintenance.repairrequest.service;

import cn.wzbc.maintenance.account.mapper.UserMapper;
import cn.wzbc.maintenance.account.entity.UserEntity;
import cn.wzbc.maintenance.account.vo.CurrentUserVo;
import cn.wzbc.maintenance.common.*;
import cn.wzbc.maintenance.equipment.entity.EquipmentEntity;
import cn.wzbc.maintenance.equipment.mapper.EquipmentMapper;
import cn.wzbc.maintenance.equipment.service.EquipmentService;
import cn.wzbc.maintenance.repairrequest.dto.*;
import cn.wzbc.maintenance.repairrequest.entity.*;
import cn.wzbc.maintenance.repairrequest.mapper.RepairRequestMapper;
import cn.wzbc.maintenance.security.*;
import cn.wzbc.maintenance.workorder.service.AssignmentService;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static cn.wzbc.maintenance.common.OperationResultService.*;

@Service
public class RequestReviewService {
    private final RepairRequestMapper requests; private final UserMapper users; private final EquipmentMapper equipment;
    private final EquipmentService deviceRules; private final AssignmentService assignments; private final BusinessAccessPolicy access; private final OperationResultService operations; private final Clock clock;
    public RequestReviewService(RepairRequestMapper requests,UserMapper users,EquipmentMapper equipment,EquipmentService deviceRules,AssignmentService assignments,BusinessAccessPolicy access,OperationResultService operations,Clock clock) { this.requests=requests; this.users=users; this.equipment=equipment; this.deviceRules=deviceRules; this.assignments=assignments; this.access=access; this.operations=operations; this.clock=clock; }
    private record Locked(RepairRequestEntity request,EquipmentEntity equipment,List<UserEntity> users) {}

    private Locked lock(CurrentUser actor,long requestId,Long equipmentId,Long technicianId) {
        access.requireUser(actor,Set.of(CurrentUser.Role.ADMIN),false);
        var before=requests.selectById(requestId); if(before==null) throw error(404,"RESOURCE_NOT_FOUND","申请不存在");
        var ids=Stream.of(actor.id(),before.reporterId,technicianId).filter(Objects::nonNull).distinct().sorted().toList();
        var lockedUsers=users.lockOrdered(ids); access.requireUser(actor,Set.of(CurrentUser.Role.ADMIN),false);
        var device=equipmentId==null?null:equipment.lock(equipmentId); var current=requests.lock(requestId);
        if(current==null || !current.reporterId.equals(before.reporterId)) throw error(409,"STATE_CONFLICT","申请归属已变化，请重新核对");
        return new Locked(current,device,lockedUsers);
    }
    private static void pending(RepairRequestEntity request,Integer suppliedVersion) {
        version(request.version,suppliedVersion);
        if(!request.status.equals("PENDING_REVIEW")) throw error(409,"STATE_CONFLICT","只有待审核申请可审核或重复关闭");
        if(request.currentSubmissionId==null) throw error(409,"STATE_CONFLICT","申请原材料不可用，请重新核对");
        if(request.locationMode.equals("STRUCTURED") && request.locationId==null) throw error(409,"LOCATION_MISMATCH","结构化申请缺少合法位置，请退回补充");
    }
    // The pre-lock identity lookup must not create a stale RR snapshot for operation replay.
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Receipt approveAndAssign(CurrentUser actor,long requestId,ApproveAndAssignDto dto) {
        uuid(dto.operationId()); Long deviceId=dto.equipmentId()==null?null:id(dto.equipmentId()); long technicianId=id(dto.technicianId());
        byte[] digest=operations.digest(Arrays.asList("APPROVE_ASSIGN",requestId,dto.version(),null,deviceId,technicianId,dto.confirmPreviouslyRefused()));
        var locked=lock(actor,requestId,deviceId,technicianId); var request=locked.request();
        var replay=operations.replay(actor,"APPROVE_ASSIGN",dto.operationId(),digest); if(replay!=null) return replay;
        pending(request,dto.version());
        var reporter=locked.users().stream().filter(u -> u.id.equals(request.reporterId)).findFirst().orElse(null);
        if(reporter==null || !reporter.enabled || reporter.role!=CurrentUser.Role.REPORTER) throw error(409,"QUALIFICATION_CHANGED","报修用户身份已变化，请重新核对");
        var technician=locked.users().stream().filter(u -> u.id==technicianId).findFirst().orElse(null);
        if(!AssignmentService.eligible(technician)) throw error(409,"QUALIFICATION_CHANGED","没有有效的合格维修人员，请重新选择");
        // Preserve old explicit-link operations (including UNKNOWN replay); new clients omit equipment.
        if(deviceId!=null) {
            var device=locked.equipment(); if(device==null) throw error(404,"RESOURCE_NOT_FOUND","设备不存在");
            if(!device.enabled) throw error(409,"EQUIPMENT_DISABLED","停用设备不能受理新工单"); deviceRules.validateReviewMatch(device,request.locationId);
            if(equipment.unfinished(deviceId)>0) throw error(409,"EQUIPMENT_HAS_UNFINISHED_ORDER","所选具体设备已有未完结工单，请核实重复情况");
        }
        Instant now=clock.instant().truncatedTo(ChronoUnit.MILLIS); change(request,"APPROVED",deviceId,now);
        var order=assignments.first(request,deviceId,technician,actor,now);
        event(request,actor,"APPROVED_ASSIGNED",null,deviceId,"APPROVE_ASSIGN",dto.operationId(),digest,0,now);
        return new Receipt(dto.operationId(),"REPAIR_REQUEST",request.id.toString(),request.version,0,CurrentUserVo.instant(now),"WORK_ORDER",order.id.toString());
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Receipt duplicateClose(CurrentUser actor,long requestId,DuplicateCloseDto dto) {
        uuid(dto.operationId()); long deviceId=id(dto.equipmentId()); String reason=text(dto.reason(),2000,"重复关闭原因");
        byte[] digest=operations.digest(Arrays.asList("DUPLICATE_CLOSE",requestId,dto.version(),null,deviceId,reason));
        var locked=lock(actor,requestId,deviceId,null); var request=locked.request();
        var replay=operations.replay(actor,"DUPLICATE_CLOSE",dto.operationId(),digest); if(replay!=null) return replay;
        pending(request,dto.version()); var device=locked.equipment(); if(device==null) throw error(404,"RESOURCE_NOT_FOUND","设备不存在"); deviceRules.validateReviewMatch(device,request.locationId);
        Instant now=clock.instant().truncatedTo(ChronoUnit.MILLIS); change(request,"DUPLICATE_CLOSED",deviceId,now);
        event(request,actor,"DUPLICATE_CLOSED",reason,deviceId,"DUPLICATE_CLOSE",dto.operationId(),digest,null,now);
        return new Receipt(dto.operationId(),"REPAIR_REQUEST",request.id.toString(),request.version,null,CurrentUserVo.instant(now),null,null);
    }
    private void change(RepairRequestEntity request,String status,Long deviceId,Instant now) {
        if(requests.update(null,new UpdateWrapper<RepairRequestEntity>().eq("id",request.id).eq("version",request.version).eq("status","PENDING_REVIEW").set("status",status).set("matched_equipment_id",deviceId).set("version",request.version+1).set("updated_at",now))!=1) throw error(409,"VERSION_CONFLICT","申请已变化，请重新核对");
        request.status=status; request.matchedEquipmentId=deviceId; request.version++; request.updatedAt=now;
    }
    private void event(RepairRequestEntity request,CurrentUser actor,String type,String reason,Long deviceId,String operationType,String operationId,byte[] digest,Integer round,Instant now) {
        var event=new RequestEventEntity(); event.id=IdWorker.getId(); event.createdAt=now; event.requestId=request.id; event.actorUserId=actor.id(); event.submissionId=request.currentSubmissionId; event.eventType=type; event.fromStatus="PENDING_REVIEW"; event.toStatus=request.status; event.reason=reason; event.occurredAt=now; event.evidenceEquipmentId=deviceId;
        event.operationType=operationType; event.operationId=operationId; event.contentDigest=digest; event.resultResourceType="REPAIR_REQUEST"; event.resultResourceId=request.id; event.resultVersion=request.version; event.resultRound=round; requests.insertEvent(event);
    }
}
