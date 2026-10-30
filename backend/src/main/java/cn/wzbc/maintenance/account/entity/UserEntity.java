package cn.wzbc.maintenance.account.entity;

import cn.wzbc.maintenance.security.CurrentUser;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

@TableName("user_account")
public class UserEntity {
    @TableId public Long id;
    public Instant createdAt;
    public String loginName;
    public String name;
    public String phone;
    public String passwordHash;
    public CurrentUser.Role role;
    public boolean enabled;
    public CurrentUser.ReviewStatus reviewStatus;
    public int reviewAttempt;
    public String provisionKey;
    public int version;
    public Instant updatedAt;
    public CurrentUser identity() { return new CurrentUser(id,role,enabled,reviewStatus,version); }
}
