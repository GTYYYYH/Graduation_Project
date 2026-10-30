package cn.wzbc.maintenance.workorder.vo;
import cn.wzbc.maintenance.repairrequest.vo.RepairRequestDetailVo.*;
import cn.wzbc.maintenance.attachment.vo.AttachmentVo;
import java.util.List;
public record WorkOrderDetailVo(String id,String workOrderNo,String requestId,String requestNo,String reportedEquipmentName,EquipmentReferenceVo equipment,String status,String locationMode,LocationReferenceVo location,String locationNameSnapshot,String createdAt,
    AccountReferenceVo assignedTechnician,AccountReferenceVo acceptedTechnician,Integer version,Integer round,Boolean isRework,List<String> allowedActions,String historyScope,
    SubmissionVo requestMaterial,AccountReferenceVo reporter,List<AssignmentVo> assignments,List<RepairRoundVo> rounds,List<EventVo> events,String firstSubmittedAt,String acceptedAt,String completedAt,String queriedAt) {
    public record SummaryVo(String id,String workOrderNo,String requestId,String requestNo,String reportedEquipmentName,EquipmentReferenceVo equipment,String status,String locationMode,LocationReferenceVo location,String locationNameSnapshot,String createdAt,
        AccountReferenceVo assignedTechnician,AccountReferenceVo acceptedTechnician,Integer version,Integer round,Boolean isRework,List<String> allowedActions,String historyScope) {}
    public record AssignmentVo(String id,int assignmentNo,AccountReferenceVo technician,AccountReferenceVo assignedBy,String visibleSubmissionId,String assignedAt,String endedAt,String outcome,String reason,String decisionAt) {}
    public record ProgressVo(String id,int round,AccountReferenceVo technician,String content,String recordedAt) {}
    public record AcceptanceVo(boolean passed,String reason,AccountReferenceVo reporter,String acceptedAt) {}
    public record RepairRoundVo(String id,int round,AccountReferenceVo technician,String startedAt,String submittedAt,String result,List<AttachmentVo> attachments,AcceptanceVo acceptance,List<ProgressVo> progress) {}
    public record EventVo(String id,String type,String fromStatus,String toStatus,AccountReferenceVo actor,String reason,String occurredAt) {}
}
