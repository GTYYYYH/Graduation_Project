package cn.wzbc.maintenance.account.service;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import cn.wzbc.maintenance.security.PasswordSupport;
import cn.wzbc.maintenance.account.entity.UserEntity;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import cn.wzbc.maintenance.common.OperationResultService;
import static cn.wzbc.maintenance.common.OperationResultService.*;
@Service
public class AdminPasswordMaintenanceService {
    private final AccountMutationSupport accounts;private final PasswordSupport passwords;private final SessionService sessions;private final OperationResultService operations;
    public AdminPasswordMaintenanceService(AccountMutationSupport accounts,PasswordSupport passwords,SessionService sessions,OperationResultService operations){this.accounts=accounts;this.passwords=passwords;this.sessions=sessions;this.operations=operations;}
    public record Result(long userId,String newPassword) {}
    @Transactional public Result reset(){
        var found=accounts.users.provisionedAdmin();if(found==null)throw error(404,"RESOURCE_NOT_FOUND","预置管理员尚未初始化");var u=accounts.users.lock(found.id);version(u.version,u.version);
        String secret=passwords.randomValidPassword();if(accounts.users.update(null,new UpdateWrapper<UserEntity>().eq("id",u.id).eq("version",u.version).set("password_hash",passwords.hash(secret)).set("version",u.version+1).set("updated_at",accounts.now()))!=1)throw error(409,"VERSION_CONFLICT","管理员资料已变化");
        sessions.revokeAll(u.id,"PASSWORD_RESET");u.version++;String operation=java.util.UUID.randomUUID().toString();accounts.record(u.identity(),u,"PASSWORD_RESET",operation,operations.digest(java.util.List.of("LOCAL_ADMIN_RESET",u.id,operation)));return new Result(u.id,secret);
    }
}
