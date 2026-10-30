package cn.wzbc.maintenance.workorder.service;

import cn.wzbc.maintenance.attachment.AttachmentTarget;
import cn.wzbc.maintenance.attachment.service.AttachmentService;
import cn.wzbc.maintenance.common.OperationResultService.Receipt;
import cn.wzbc.maintenance.security.CurrentUser;
import cn.wzbc.maintenance.workorder.dto.*;
import cn.wzbc.maintenance.workorder.entity.ProgressEntity;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static cn.wzbc.maintenance.common.OperationResultService.*;

@Service
public class RepairProcessingService {
    private final WorkOrderMutationSupport work; private final AttachmentService attachments;
    public RepairProcessingService(WorkOrderMutationSupport work,AttachmentService attachments) { this.work=work; this.attachments=attachments; }
    private void current(CurrentUser actor,cn.wzbc.maintenance.workorder.entity.WorkOrderEntity order,Integer round) {
        if(order.acceptedTechnicianId==null || order.acceptedTechnicianId!=actor.id()) throw error(403,"RECORD_FORBIDDEN","不是当前实际维修人员");
        if(actor.reviewStatus()!=CurrentUser.ReviewStatus.APPROVED) throw error(403,"REVIEW_REQUIRED","当前没有维修资格");
        if(round==null || round<1 || order.currentRound!=round) throw error(409,"ROUND_CONFLICT","维修轮次已变化");
        if(!order.status.equals("IN_PROGRESS")) throw error(409,"STATE_CONFLICT","当前不可填写维修过程");
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Receipt progress(CurrentUser actor,long id,ProgressDto dto) {
        uuid(dto.operationId()); String content=text(dto.content(),4000,"维修进度").trim();
        byte[] digest=work.operations.digest(Arrays.asList("PROGRESS_ADDED",id,dto.version(),dto.round(),content));
        var locked=work.lock(actor,id,Set.of(CurrentUser.Role.TECHNICIAN),null); var order=locked.order();
        var replay=work.replay(actor,"PROGRESS_ADDED",dto.operationId(),digest); if(replay!=null) return replay;
        locked.requireCurrentParticipants();
        current(actor,order,dto.round()); version(order.version,dto.version());
        var round=work.orders.lockRound(id,order.currentRound); if(round==null || round.technicianId!=actor.id() || round.submittedAt!=null) throw error(409,"ROUND_CONFLICT","本轮维修已变化");
        var now=work.now(); var entry=new ProgressEntity(); entry.id=IdWorker.getId(); entry.createdAt=now; entry.recordedAt=now; entry.workOrderId=id; entry.roundId=round.id; entry.technicianId=actor.id(); entry.content=content;
        work.orders.insertProgress(entry);
        work.change(order,"IN_PROGRESS","IN_PROGRESS",order.assignedTechnicianId,order.currentAssignmentId,order.acceptedTechnicianId,order.acceptedAt,null,order.currentRound,now);
        return work.event(order,actor,"PROGRESS_ADDED","IN_PROGRESS","IN_PROGRESS",null,order.currentAssignmentId,round.id,entry.id,dto.operationId(),digest,id,now);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Receipt submit(CurrentUser actor,long id,SubmitRepairResultDto dto) {
        uuid(dto.operationId()); String result=text(dto.result(),4000,"本轮处理结果").trim();
        var ids=attachmentIds(dto.attachmentIds()); if(ids.isEmpty()) throw error(400,"ATTACHMENT_COUNT_INVALID","本轮至少需要1张新维修凭证");
        byte[] digest=work.operations.digest(Arrays.asList("RESULT_SUBMITTED",id,dto.version(),dto.round(),result,ids));
        var locked=work.lock(actor,id,Set.of(CurrentUser.Role.TECHNICIAN),null); var order=locked.order();
        var replay=work.replay(actor,"RESULT_SUBMITTED",dto.operationId(),digest); if(replay!=null) return replay;
        locked.requireCurrentParticipants();
        current(actor,order,dto.round()); version(order.version,dto.version());
        var round=work.orders.lockRound(id,order.currentRound); if(round==null || round.technicianId!=actor.id() || round.submittedAt!=null) throw error(409,"ROUND_CONFLICT","本轮维修已变化");
        attachments.bindTemporary(ids,new AttachmentTarget("REPAIR_ROUND",round.id,round.roundNo),actor);
        var now=work.now(); round.submittedAt=now; round.resultText=result;
        if(work.orders.submitRound(round)!=1) throw error(409,"ROUND_CONFLICT","本轮维修已变化");
        work.change(order,"IN_PROGRESS","PENDING_ACCEPTANCE",order.assignedTechnicianId,order.currentAssignmentId,order.acceptedTechnicianId,order.acceptedAt,null,order.currentRound,now);
        return work.event(order,actor,"RESULT_SUBMITTED","IN_PROGRESS","PENDING_ACCEPTANCE",null,order.currentAssignmentId,round.id,null,dto.operationId(),digest,id,now);
    }
}
