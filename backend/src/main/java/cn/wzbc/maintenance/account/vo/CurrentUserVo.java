package cn.wzbc.maintenance.account.vo;

import cn.wzbc.maintenance.account.entity.UserEntity;
import cn.wzbc.maintenance.security.CurrentUser;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public record CurrentUserVo(String id,String loginName,String name,String phone,CurrentUser.Role role,
        boolean enabled,CurrentUser.ReviewStatus reviewStatus,int version,AccountCapabilities capabilities) {
    public record AccountCapabilities(boolean canBrowseCampus,boolean canCreateRequest,boolean canWorkOnTasks,boolean canManage) {}
    public record AuthSessionVo(CurrentUserVo user,String expiresAt) {}
    public record CsrfVo(String token,String headerName) {}
    public static CurrentUserVo from(UserEntity u) {
        return new CurrentUserVo(u.id.toString(),u.loginName,u.name,u.phone,u.role,u.enabled,u.reviewStatus,u.version,
            new AccountCapabilities(u.enabled,u.enabled && u.role==CurrentUser.Role.REPORTER,
                u.enabled && u.role==CurrentUser.Role.TECHNICIAN && u.reviewStatus==CurrentUser.ReviewStatus.APPROVED,
                u.enabled && u.role==CurrentUser.Role.ADMIN));
    }
    public static String instant(Instant value) { return value==null ? null : DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC).format(value); }
}
