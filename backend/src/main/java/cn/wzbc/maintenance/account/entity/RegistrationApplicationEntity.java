package cn.wzbc.maintenance.account.entity;

import cn.wzbc.maintenance.security.CurrentUser;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

@TableName("registration_review")
public class RegistrationApplicationEntity {
    @TableId public Long id;
    public Instant createdAt;
    public Long userId;
    public int attempt;
    public String nameSnapshot;
    public String phoneSnapshot;
    public Instant submittedAt;
    public CurrentUser.ReviewStatus status;
    public Long reviewedBy;
    public Instant reviewedAt;
    public String reason;
    public int version;
}
