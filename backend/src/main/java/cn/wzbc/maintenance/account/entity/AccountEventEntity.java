package cn.wzbc.maintenance.account.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
@TableName("account_event")
public class AccountEventEntity {
    @TableId public Long id;
    public Instant createdAt;
    public Long actorUserId;
    public Long targetUserId;
    public String eventType;
    public Instant occurredAt;
    public Long reviewId;
    public String reason;
    public String operationType;
    public String operationId;
    public byte[] contentDigest;
    public Integer resultVersion;
    public Long resultResourceId;
}
