package cn.wzbc.maintenance.account.vo;

import cn.wzbc.maintenance.security.CurrentUser;
public record RegistrationVo(String id,String loginName,CurrentUser.Role role,CurrentUser.ReviewStatus reviewStatus) {}
