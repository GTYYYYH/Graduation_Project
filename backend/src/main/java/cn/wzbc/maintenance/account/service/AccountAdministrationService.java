package cn.wzbc.maintenance.account.service;
import cn.wzbc.maintenance.account.dto.*;
import cn.wzbc.maintenance.account.entity.UserEntity;
import cn.wzbc.maintenance.account.vo.PasswordResetResultVo;
import cn.wzbc.maintenance.common.OperationResultService;
import cn.wzbc.maintenance.common.OperationResultService.Receipt;
import cn.wzbc.maintenance.security.*;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static cn.wzbc.maintenance.common.OperationResultService.*;
@Service
public class AccountAdministrationService {
    private final AccountMutationSupport accounts;private final OperationResultService operations;private final PasswordSupport passwords;private final SessionService sessions;private final JdbcTemplate db;
    public AccountAdministrationService(AccountMutationSupport accounts,OperationResultService operations,PasswordSupport passwords,SessionService sessions,JdbcTemplate db){this.accounts=accounts;this.operations=operations;this.passwords=passwords;this.sessions=sessions;this.db=db;}
    private UserEntity lock(CurrentUser actor,long target){
        accounts.access.requireUser(actor,Set.of(CurrentUser.Role.ADMIN),false);accounts.users.lockOrdered(java.util.stream.Stream.of(actor.id(),target).distinct().sorted().toList());accounts.access.requireUser(actor,Set.of(CurrentUser.Role.ADMIN),false);
        var u=accounts.users.selectById(target);if(u==null)throw error(404,"RESOURCE_NOT_FOUND","人员不存在");return u;
    }
    @Transactional(isolation=Isolation.READ_COMMITTED) public Receipt status(CurrentUser actor,long target,AccountStatusDto dto,boolean enabled){
        var u=lock(actor,target);uuid(dto.operationId());String type=enabled?"ACCOUNT_ENABLE":"ACCOUNT_DISABLE";byte[] digest=operations.digest(List.of(type,target,dto.version()));
        var replay=operations.replay(actor,type,dto.operationId(),digest);if(replay!=null)return replay;version(u.version,dto.version());
        if(!enabled&&actor.id()==target)throw error(409,"CANNOT_DISABLE_SELF","不能停用当前登录管理员");
        if(u.enabled==enabled)throw error(409,"STATE_CONFLICT","账号状态已变化，请重新核对");
        if(!enabled){var blockers=db.queryForList("SELECT request_no business_no,status FROM repair_request WHERE reporter_id=? AND status IN ('PENDING_REVIEW','NEEDS_SUPPLEMENT') UNION ALL SELECT work_order_no business_no,status FROM work_order WHERE status<>'COMPLETED' AND (reporter_id=? OR assigned_technician_id=? OR accepted_technician_id=?)",target,target,target,target);
            if(!blockers.isEmpty())throw error(409,"UNFINISHED_BUSINESS","存在未完结业务，不能停用："+blockers.stream().map(b->b.get("business_no")+"（"+b.get("status")+"）").collect(java.util.stream.Collectors.joining("、")));
        }
        change(u,new UpdateWrapper<UserEntity>().set("enabled",enabled));if(!enabled)sessions.revokeAll(target,"ACCOUNT_DISABLED");return accounts.record(actor,u,enabled?"ENABLED":"DISABLED",dto.operationId(),digest);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED) public PasswordResetResultVo reset(CurrentUser actor,long target,ResetPasswordDto dto){
        var u=lock(actor,target);if(u.role==CurrentUser.Role.ADMIN)throw error(403,"ADMIN_LOCAL_RESET_REQUIRED","管理员遗忘密码须由维护者在本地重置");uuid(dto.operationId());
        byte[] digest=operations.digest(List.of("PASSWORD_RESET",target,dto.version()));var replay=operations.replay(actor,"PASSWORD_RESET",dto.operationId(),digest);
        if(replay!=null)return result(u,replay,null);version(u.version,dto.version());String secret=passwords.randomValidPassword();
        change(u,new UpdateWrapper<UserEntity>().set("password_hash",passwords.hash(secret)));sessions.revokeAll(target,"PASSWORD_RESET");return result(u,accounts.record(actor,u,"PASSWORD_RESET",dto.operationId(),digest),secret);
    }
    private void change(UserEntity u,UpdateWrapper<UserEntity> update){if(accounts.users.update(null,update.eq("id",u.id).eq("version",u.version).set("version",u.version+1).set("updated_at",accounts.now()))!=1)throw error(409,"VERSION_CONFLICT","账号已变化");u.version++;}
    private PasswordResetResultVo result(UserEntity u,Receipt receipt,String secret){return new PasswordResetResultVo(secret==null?"ALREADY_COMMITTED":"NEW_SECRET",receipt,new PasswordResetResultVo.AccountReference(u.id.toString(),u.name),u.loginName,secret);}
}

