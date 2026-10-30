package cn.wzbc.maintenance.workorder.service;

import cn.wzbc.maintenance.common.OperationResultService.Receipt;
import cn.wzbc.maintenance.security.CurrentUser;
import cn.wzbc.maintenance.workorder.dto.*;
import cn.wzbc.maintenance.workorder.entity.RepairRoundEntity;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static cn.wzbc.maintenance.common.OperationResultService.*;

@Service
public class WorkOrderTransitionService {
    private final WorkOrderMutationSupport work;
    public WorkOrderTransitionService(WorkOrderMutationSupport work) { this.work=work; }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Receipt accept(CurrentUser actor,long id,AcceptWorkOrderDto dto) {
        uuid(dto.operationId()); byte[] digest=work.operations.digest(Arrays.asList("ACCEPTED",id,dto.version()));
        var locked=work.lock(actor,id,Set.of(CurrentUser.Role.TECHNICIAN),null); var order=locked.order();
        var replay=work.replay(actor,"ACCEPTED",dto.operationId(),digest); if(replay!=null) return replay;
        locked.requireCurrentParticipants();
        if(order.assignedTechnicianId==null || order.assignedTechnicianId!=actor.id()) throw error(403,"RECORD_FORBIDDEN","不是当前分派人员");
        if(actor.reviewStatus()!=CurrentUser.ReviewStatus.APPROVED) throw error(403,"REVIEW_REQUIRED","当前没有维修资格");
        version(order.version,dto.version()); if(!order.status.equals("PENDING_ACCEPT") || order.currentRound!=0) throw error(409,"STATE_CONFLICT","当前不可接单");
        var assignment=work.orders.lockAssignment(order.currentAssignmentId);
        if(assignment==null || assignment.technicianId!=actor.id() || !assignment.outcome.equals("PENDING")) throw error(409,"STATE_CONFLICT","当前分派已变化");
        var now=work.now(); assignment.decisionAt=now;
        if(work.orders.acceptAssignment(assignment)!=1) throw error(409,"STATE_CONFLICT","当前分派已变化");
        var round=new RepairRoundEntity(); round.id=IdWorker.getId(); round.createdAt=now; round.workOrderId=id; round.roundNo=1; round.technicianId=actor.id(); round.startedAt=now;
        work.orders.insertRound(round);
        work.change(order,"PENDING_ACCEPT","IN_PROGRESS",actor.id(),assignment.id,actor.id(),now,null,1,now);
        return work.event(order,actor,"ACCEPTED","PENDING_ACCEPT","IN_PROGRESS",null,assignment.id,round.id,null,dto.operationId(),digest,id,now);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Receipt refuse(CurrentUser actor,long id,RefuseWorkOrderDto dto) {
        uuid(dto.operationId()); String reason=text(dto.reason(),2000,"拒单原因").trim(); byte[] digest=work.operations.digest(Arrays.asList("REFUSED",id,dto.version(),reason));
        var locked=work.lock(actor,id,Set.of(CurrentUser.Role.TECHNICIAN),null); var order=locked.order();
        var replay=work.replay(actor,"REFUSED",dto.operationId(),digest); if(replay!=null) return replay;
        locked.requireCurrentParticipants();
        if(order.assignedTechnicianId==null || order.assignedTechnicianId!=actor.id()) throw error(403,"RECORD_FORBIDDEN","不是当前分派人员");
        if(actor.reviewStatus()!=CurrentUser.ReviewStatus.APPROVED) throw error(403,"REVIEW_REQUIRED","当前没有维修资格");
        version(order.version,dto.version()); if(!order.status.equals("PENDING_ACCEPT") || order.currentRound!=0) throw error(409,"STATE_CONFLICT","当前不可拒单");
        var assignment=work.orders.lockAssignment(order.currentAssignmentId);
        if(assignment==null || assignment.technicianId!=actor.id() || !assignment.outcome.equals("PENDING")) throw error(409,"STATE_CONFLICT","当前分派已变化");
        var now=work.now(); assignment.endedAt=now; assignment.decisionAt=now; assignment.reason=reason; assignment.outcome="REFUSED";
        if(work.orders.endAssignment(assignment)!=1) throw error(409,"STATE_CONFLICT","当前分派已变化");
        work.change(order,"PENDING_ACCEPT","REASSIGN_PENDING",null,null,null,null,null,0,now);
        return work.event(order,actor,"REFUSED","PENDING_ACCEPT","REASSIGN_PENDING",reason,assignment.id,null,null,dto.operationId(),digest,id,now);
    }
}
