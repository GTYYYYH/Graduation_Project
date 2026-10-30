package cn.wzbc.maintenance.repairrequest.entity;
import java.time.Instant;
public class RequestSubmissionEntity {
    public Long id,requestId,actorUserId,locationId;
    public Instant createdAt,submittedAt;
    public int submissionNo,resultVersion;
    public String equipmentName,equipmentCategory,addressText,description,contactName,contactPhone,locationMode,locationNameSnapshot,operationType,operationId;
    public byte[] contentDigest;
}
