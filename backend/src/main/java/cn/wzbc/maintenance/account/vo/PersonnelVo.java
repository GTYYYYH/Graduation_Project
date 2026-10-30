package cn.wzbc.maintenance.account.vo;
import cn.wzbc.maintenance.security.CurrentUser;
import java.util.*;
public record PersonnelVo(String id,String loginName,String name,String phone,CurrentUser.Role role,boolean enabled,CurrentUser.ReviewStatus reviewStatus,int version,CurrentUserVo.AccountCapabilities capabilities,List<String> allowedActions,int reviewAttempt,Map<String,Object> counts) {}
