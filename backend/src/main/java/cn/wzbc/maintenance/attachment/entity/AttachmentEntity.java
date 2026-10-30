package cn.wzbc.maintenance.attachment.entity;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
@TableName("attachment_file")
public class AttachmentEntity {
    @TableId public Long id;
    public Instant createdAt;
    public Long uploaderId;
    public String uploadOperationId, purpose, state, batchId;
    public Long targetRequestId, targetWorkOrderId;
    public Integer targetRound;
    public String originalName, storageKey, mediaType;
    public long byteSize;
    public byte[] fileDigest;
    public Instant uploadedAt, expiresAt;
    public Long boundSubmissionId, boundRoundId;
    public Instant boundAt, deletionClaimedAt, deletedAt;
    public int deleteAttempts;
}
