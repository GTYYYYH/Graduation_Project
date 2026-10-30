package cn.wzbc.maintenance.workorder.service;

import cn.wzbc.maintenance.common.OperationResultService.Receipt;
import cn.wzbc.maintenance.security.CurrentUser;
import cn.wzbc.maintenance.workorder.dto.AcceptanceDto;
import cn.wzbc.maintenance.workorder.entity.RepairRoundEntity;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static cn.wzbc.maintenance.common.OperationResultService.*;

@Service
public class AcceptanceService {
    private final WorkOrderMutationSupport work;
    public AcceptanceService(WorkOrderMutationSupport work) { this.work=work; }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Receipt decide(CurrentUser actor,long id,AcceptanceDto dto) {
        uuid(dto.operationId()); if(dto.passed()==null) throw error(400,"VALIDATION_FAILED","请选择验收结果");
        if(dto.passed() && dto.reason()!=null) throw error(400,"VALIDATION_FAILED","验收通过不接受退回原因");
        String reason=dto.passed()?null:text(dto.reason(),2000,"验收不通过原因").trim();
        byte[] digest=work.operations.digest(Arrays.asList("ACCEPTANCE",id,dto.version(),dto.round(),dto.passed(),reason));
        var locked=work.lock(actor,id,Set.of(CurrentUser.Role.REPORTER),null); var order=locked.order();
        var replay=work.replay(actor,"ACCEPTANCE",dto.operationId(),digest); if(replay!=null) return replay;
        locked.requireCurrentParticipants();
        if(order.reporterId!=actor.id()) throw error(403,"RECORD_FORBIDDEN","仅原报修人可以验收");
        version(order.version,dto.version());
        if(dto.round()==null || dto.round()<1 || dto.round()!=order.currentRound) throw error(409,"ROUND_CONFLICT","验收轮次已变化");
        if(!order.status.equals("PENDING_ACCEPTANCE")) throw error(409,"STATE_CONFLICT","当前不可验收");
        var round=work.orders.lockRound(id,order.currentRound);
        if(round==null || round.submittedAt==null || round.acceptanceAt!=null) throw error(409,"ROUND_CONFLICT","本轮结果不可验收");
        var now=work.now(); round.acceptancePassed=dto.passed(); round.acceptanceReason=reason; round.acceptedBy=actor.id(); round.acceptanceAt=now;
        if(work.orders.acceptRound(round)!=1) throw error(409,"ROUND_CONFLICT","本轮已验收");
        if(dto.passed()) {
            work.change(order,"PENDING_ACCEPTANCE","COMPLETED",order.assignedTechnicianId,order.currentAssignmentId,order.acceptedTechnicianId,order.acceptedAt,now,order.currentRound,now);
            return work.event(order,actor,"COMPLETED","PENDING_ACCEPTANCE","COMPLETED",null,order.currentAssignmentId,round.id,null,dto.operationId(),digest,id,now);
        }
        var next=new RepairRoundEntity(); next.id=IdWorker.getId(); next.createdAt=now; next.workOrderId=id; next.roundNo=order.currentRound+1; next.technicianId=order.acceptedTechnicianId; next.startedAt=now;
        work.orders.insertRound(next);
        work.change(order,"PENDING_ACCEPTANCE","IN_PROGRESS",order.assignedTechnicianId,order.currentAssignmentId,order.acceptedTechnicianId,order.acceptedAt,null,next.roundNo,now);
        return work.event(order,actor,"ACCEPTANCE_FAILED","PENDING_ACCEPTANCE","IN_PROGRESS",reason,order.currentAssignmentId,round.id,null,dto.operationId(),digest,id,now);
    }
}
