package cn.wzbc.maintenance.repairrequest.entity;
import java.time.Instant;
public class RequestEventEntity {
    public Long id,requestId,actorUserId,submissionId,evidenceEquipmentId,resultResourceId;
    public Instant createdAt,occurredAt;
    public String eventType,fromStatus,toStatus,reason,operationType,operationId,resultResourceType;
    public byte[] contentDigest;
    public Integer resultVersion,resultRound;
}
