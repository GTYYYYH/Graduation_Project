package cn.wzbc.maintenance.security;

// 仅由后端真实会话建立；B01不生成身份，也不接受客户端传入此对象。
public record CurrentUser(long id, Role role, boolean enabled, ReviewStatus reviewStatus, int version) {
    public enum Role { REPORTER, TECHNICIAN, ADMIN }
    public enum ReviewStatus { PENDING, REJECTED, APPROVED }
}
