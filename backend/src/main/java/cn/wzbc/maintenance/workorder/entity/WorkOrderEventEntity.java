package cn.wzbc.maintenance.workorder.entity;
import com.baomidou.mybatisplus.annotation.*;
import java.time.Instant;
@TableName("work_order_event")
public class WorkOrderEventEntity {
    @TableId public Long id;
    public Long workOrderId,actorUserId,assignmentId,roundId,progressId,resultResourceId;
    public Instant createdAt,occurredAt;
    public String eventType,fromStatus,toStatus,reason,operationType,operationId;
    public byte[] contentDigest;
    public Integer resultVersion,resultRound;
}
