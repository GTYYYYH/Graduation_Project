package cn.wzbc.maintenance.account.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
@TableName("login_session")
public class SessionEntity {
    @TableId public Long id;
    public Instant createdAt;
    public Long userId;
    public byte[] tokenDigest;
    public Instant issuedAt;
    public Instant expiresAt;
    public Instant revokedAt;
    public String revokeReason;
}
