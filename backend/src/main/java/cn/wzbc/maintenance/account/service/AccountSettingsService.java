package cn.wzbc.maintenance.account.service;
import cn.wzbc.maintenance.account.dto.*;
import cn.wzbc.maintenance.account.vo.CurrentUserVo;
import cn.wzbc.maintenance.common.OperationResultService;
import cn.wzbc.maintenance.common.OperationResultService.Receipt;
import cn.wzbc.maintenance.security.*;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import cn.wzbc.maintenance.account.entity.UserEntity;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static cn.wzbc.maintenance.common.OperationResultService.*;
@Service
public class AccountSettingsService {
    private final AccountMutationSupport accounts; private final OperationResultService operations; private final PasswordSupport passwords; private final SessionService sessions;
    public AccountSettingsService(AccountMutationSupport accounts,OperationResultService operations,PasswordSupport passwords,SessionService sessions) {this.accounts=accounts;this.operations=operations;this.passwords=passwords;this.sessions=sessions;}
    public record ProfileVo(String id,String loginName,String name,String phone,CurrentUser.Role role,boolean enabled,CurrentUser.ReviewStatus reviewStatus,int version,CurrentUserVo.AccountCapabilities capabilities,List<String> allowedActions) {}
    @Transactional(readOnly=true) public ProfileVo profile(CurrentUser actor) {
        var u=accounts.own(actor,false); var current=CurrentUserVo.from(u);
        boolean edit=u.role!=CurrentUser.Role.TECHNICIAN || u.reviewStatus==CurrentUser.ReviewStatus.APPROVED;
        return new ProfileVo(u.id.toString(),u.loginName,u.name,u.phone,u.role,u.enabled,u.reviewStatus,u.version,current.capabilities(),edit?List.of("EDIT_PROFILE","CHANGE_PASSWORD"):List.of("CHANGE_PASSWORD"));
    }
    @Transactional public Receipt update(CurrentUser actor,UpdateProfileDto dto) {
        var u=accounts.own(actor,true);uuid(dto.operationId());String name=text(dto.name().trim(),100,"姓名");
        byte[] digest=operations.digest(Arrays.asList("PROFILE_UPDATED",u.id,dto.version(),name,dto.phone()));
        var replay=operations.replay(actor,"PROFILE_UPDATE",dto.operationId(),digest);if(replay!=null)return replay;
        version(u.version,dto.version());
        if(u.role==CurrentUser.Role.TECHNICIAN && u.reviewStatus!=CurrentUser.ReviewStatus.APPROVED)throw error(409,"REVIEW_REQUIRED","待审核资料只读；未通过请从审核状态重新申请");
        try {if(accounts.users.update(null,new UpdateWrapper<UserEntity>().eq("id",u.id).eq("version",u.version).set("name",name).set("phone",dto.phone()).set("version",u.version+1).set("updated_at",accounts.now()))!=1)throw error(409,"VERSION_CONFLICT","资料已变化");}
        catch(DuplicateKeyException failure){throw error(409,"PHONE_EXISTS","手机号已被占用");}
        u.version++;return accounts.record(actor,u,"PROFILE_UPDATED",dto.operationId(),digest);
    }
    public record PasswordResult(Receipt receipt,boolean changed) {}
    @Transactional public PasswordResult password(CurrentUser actor,ChangePasswordDto dto) {
        var u=accounts.own(actor,true);uuid(dto.operationId());
        int length=dto.newPassword().codePointCount(0,dto.newPassword().length());if(length<8||length>32)throw error(400,"VALIDATION_FAILED","新密码需8—32位");
        if(!dto.newPassword().equals(dto.confirmNewPassword()))throw error(400,"PASSWORD_CONFIRM_MISMATCH","两次新密码不一致");
        byte[] digest=passwords.operationDigest(Arrays.asList("PASSWORD_CHANGED",u.id,dto.version(),java.util.Base64.getEncoder().encodeToString(dto.currentPassword().getBytes(java.nio.charset.StandardCharsets.UTF_8)),java.util.Base64.getEncoder().encodeToString(dto.newPassword().getBytes(java.nio.charset.StandardCharsets.UTF_8))).toString(),dto.operationId());
        var replay=operations.replay(actor,"PASSWORD_CHANGE",dto.operationId(),digest);if(replay!=null)return new PasswordResult(replay,false);
        version(u.version,dto.version());if(!passwords.matches(dto.currentPassword(),u.passwordHash))throw error(400,"CURRENT_PASSWORD_INVALID","当前密码不正确");
        if(accounts.users.update(null,new UpdateWrapper<UserEntity>().eq("id",u.id).eq("version",u.version).set("password_hash",passwords.hash(dto.newPassword())).set("version",u.version+1).set("updated_at",accounts.now()))!=1)throw error(409,"VERSION_CONFLICT","资料已变化");
        sessions.revokeAll(u.id);u.version++;return new PasswordResult(accounts.record(actor,u,"PASSWORD_CHANGED",dto.operationId(),digest),true);
    }
}

