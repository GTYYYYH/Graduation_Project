package cn.wzbc.maintenance.repairrequest.vo;
import cn.wzbc.maintenance.attachment.vo.AttachmentVo;
import java.util.List;
public record RepairRequestDetailVo(String id,String requestNo,String equipmentName,String status,String locationMode,LocationReferenceVo location,String locationNameSnapshot,String firstSubmittedAt,String createdAt,int version,
    WorkOrderLinkVo workOrder,List<String> allowedActions,AccountReferenceVo reporter,SubmissionVo currentSubmission,List<SubmissionVo> submissions,List<EventVo> events,EquipmentReferenceVo matchedEquipment,String queriedAt) {
    public record AccountReferenceVo(String id,String name) {}
    public record LocationReferenceVo(String id,String code,String name,String floorId) {}
    public record EquipmentReferenceVo(String id,String equipmentNo,String name,String locationId) {}
    public record WorkOrderLinkVo(String id,String workOrderNo,String status) {}
    public record SummaryVo(String id,String requestNo,String equipmentName,String status,String locationMode,LocationReferenceVo location,String locationNameSnapshot,String firstSubmittedAt,String createdAt,int version,WorkOrderLinkVo workOrder,List<String> allowedActions) {}
    public record SubmissionVo(String id,int submissionNo,String equipmentName,String equipmentCategory,String locationMode,LocationReferenceVo location,String locationNameSnapshot,String addressText,String description,String contactName,String contactPhone,List<AttachmentVo> attachments,String submittedAt) {}
    public record EventVo(String id,String type,String fromStatus,String toStatus,AccountReferenceVo actor,String reason,String occurredAt) {}
}
