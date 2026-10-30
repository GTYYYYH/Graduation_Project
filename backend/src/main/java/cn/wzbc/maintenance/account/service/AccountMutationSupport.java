package cn.wzbc.maintenance.account.service;
import cn.wzbc.maintenance.account.entity.*;
import cn.wzbc.maintenance.account.mapper.*;
import cn.wzbc.maintenance.account.vo.CurrentUserVo;
import cn.wzbc.maintenance.common.OperationResultService.Receipt;
import cn.wzbc.maintenance.security.*;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Component;
@Component
public class AccountMutationSupport {
    final UserMapper users; final AccountEventMapper events; final BusinessAccessPolicy access; final Clock clock;
    public AccountMutationSupport(UserMapper users,AccountEventMapper events,BusinessAccessPolicy access,Clock clock) {this.users=users;this.events=events;this.access=access;this.clock=clock;}
    public UserEntity own(CurrentUser actor,boolean lock) {return access.requireUser(actor,Set.of(CurrentUser.Role.values()),lock);}
    public Instant now() {return clock.instant().truncatedTo(ChronoUnit.MILLIS);}
    public Receipt record(CurrentUser actor,UserEntity target,String type,String operation,byte[] digest) {
        var e=new AccountEventEntity();e.id=IdWorker.getId();e.createdAt=now();e.occurredAt=e.createdAt;e.actorUserId=actor.id();e.targetUserId=target.id;
        e.eventType=type;e.operationType=switch(type){case "PROFILE_UPDATED"->"PROFILE_UPDATE";case "PASSWORD_CHANGED"->"PASSWORD_CHANGE";case "ENABLED"->"ACCOUNT_ENABLE";case "DISABLED"->"ACCOUNT_DISABLE";default->type;};e.operationId=operation;e.contentDigest=digest;e.resultVersion=target.version;e.resultResourceId=target.id;events.insert(e);
        return new Receipt(operation,"ACCOUNT",target.id.toString(),target.version,null,CurrentUserVo.instant(e.occurredAt),null,null);
    }
}

