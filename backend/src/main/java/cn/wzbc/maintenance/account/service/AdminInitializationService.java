package cn.wzbc.maintenance.account.service;

import org.springframework.stereotype.Service;
import cn.wzbc.maintenance.account.entity.UserEntity;
import cn.wzbc.maintenance.account.mapper.UserMapper;
import cn.wzbc.maintenance.security.CurrentUser;
import cn.wzbc.maintenance.security.PasswordSupport;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import java.time.Clock;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminInitializationService {
    public record InitializationResult(Long userId, String newPassword, boolean changed) {}
    private final UserMapper users;
    private final PasswordSupport passwords;
    private final Clock clock;
    public AdminInitializationService(UserMapper users,PasswordSupport passwords,Clock clock) { this.users=users; this.passwords=passwords; this.clock=clock; }
    @Transactional
    public InitializationResult initialize(String loginName,String name,String phone) {
        var existing=users.provisionedAdmin();
        if (existing!=null) return new InitializationResult(existing.id,null,false);
        if (loginName==null || !loginName.matches("[\\p{IsHan}A-Za-z0-9_]+") || loginName.codePointCount(0,loginName.length())<2 || loginName.codePointCount(0,loginName.length())>30
                || name==null || name.isBlank() || name.trim().codePointCount(0,name.trim().length())>100 || phone==null || !phone.matches("1[3-9][0-9]{9}")) {
            throw new IllegalArgumentException("请提供合法的管理员账号、姓名及大陆手机号");
        }
        String password=passwords.randomValidPassword();
        var user=new UserEntity(); user.id=IdWorker.getId(); user.createdAt=clock.instant(); user.updatedAt=user.createdAt;
        user.loginName=loginName; user.name=name.trim(); user.phone=phone; user.passwordHash=passwords.hash(password);
        user.role=CurrentUser.Role.ADMIN; user.enabled=true; user.provisionKey="campus-admin";
        users.insert(user);
        return new InitializationResult(user.id,password,true);
    }
}
