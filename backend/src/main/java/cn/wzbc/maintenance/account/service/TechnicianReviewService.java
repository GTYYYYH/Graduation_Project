package cn.wzbc.maintenance.account.service;

import cn.wzbc.maintenance.account.dto.*;
import cn.wzbc.maintenance.account.entity.*;
import cn.wzbc.maintenance.account.mapper.*;
import cn.wzbc.maintenance.account.vo.*;
import cn.wzbc.maintenance.common.*;
import cn.wzbc.maintenance.security.CurrentUser;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class TechnicianReviewService {
    private final UserMapper users;
    private final RegistrationApplicationMapper reviews;
    private final AccountEventMapper events;
    private final Clock clock;
    private final ObjectMapper json;
    public TechnicianReviewService(UserMapper users,RegistrationApplicationMapper reviews,AccountEventMapper events,Clock clock,ObjectMapper json) {
        this.users=users; this.reviews=reviews; this.events=events; this.clock=clock; this.json=json;
    }
    @Transactional(readOnly=true)
    public TechnicianReviewVo own(CurrentUser actor) {
        var account=actor(actor,CurrentUser.Role.TECHNICIAN,false);
        return detail(account,false);
    }
    @Transactional(readOnly=true)
    public TechnicianReviewVo administrative(CurrentUser actor,long target) {
        actor(actor,CurrentUser.Role.ADMIN,false);
        var account=users.selectById(target); technician(account);
        return detail(account,true);
    }
    @Transactional(readOnly=true)
    public PageResult<TechnicianReviewVo.PendingReviewVo> pending(CurrentUser actor,int page,int pageSize,String keyword) {
        actor(actor,CurrentUser.Role.ADMIN,false);
        if (page<1 || pageSize<1 || pageSize>100 || (keyword!=null && keyword.codePointCount(0,keyword.length())>100)) throw error(400,"INVALID_QUERY","分页或搜索条件不合法");
        var query=new QueryWrapper<UserEntity>().eq("role","TECHNICIAN").eq("review_status","PENDING");
        if (keyword!=null && !keyword.isBlank()) query.and(q -> q.like("name",keyword.trim()).or().like("login_name",keyword.trim()).or().like("phone",keyword.trim()));
        long total=users.selectCount(query);
        query.orderByAsc("created_at","id").last("LIMIT "+pageSize+" OFFSET "+((long)(page-1)*pageSize));
        var records=users.selectList(query).stream().map(u -> {
            var history=reviews.history(u.id); var current=history.isEmpty() ? null:history.getFirst();
            return new TechnicianReviewVo.PendingReviewVo(u.id.toString(),u.loginName,u.name,u.phone,u.enabled,u.reviewAttempt,current==null ? null:CurrentUserVo.instant(current.submittedAt));
        }).toList();
        return new PageResult<>(records,total,page,pageSize);
    }
    @Transactional
    public TechnicianReviewVo.MutationReceiptVo decide(CurrentUser actor,long target,ReviewDecisionDto dto) {
        if (actor==null || actor.role()!=CurrentUser.Role.ADMIN) throw error(403,"FORBIDDEN","仅管理员可审核");
        var locked=users.lockOrdered(java.util.stream.Stream.of(actor.id(),target).distinct().sorted().toList());
        var admin=locked.stream().filter(u -> u.id==actor.id()).findFirst().orElse(null); requireActor(admin,CurrentUser.Role.ADMIN);
        var account=locked.stream().filter(u -> u.id==target).findFirst().orElse(null); technician(account);
        String reason=dto.reason()==null ? null:dto.reason().trim();
        if ((!dto.passed() && (reason==null || reason.isBlank())) || (reason!=null && reason.codePointCount(0,reason.length())>2000)) throw error(400,"VALIDATION_FAILED","未通过原因必填，且不超过2000字");
        byte[] digest=digest(Arrays.asList("REVIEW_DECIDE",target,dto.version(),dto.attempt(),dto.reviewVersion(),dto.passed(),reason));
        var replay=replay(actor.id(),"REVIEW_DECIDE",dto.operationId(),target,digest); if (replay!=null) return replay;
        if (!account.enabled) throw error(409,"STATE_CONFLICT","账号已停用，请先启用原账号后再审核");
        var review=reviews.lockCurrent(account.id,account.reviewAttempt);
        validateVersions(account,review,dto.version(),dto.attempt(),dto.reviewVersion());
        if (account.reviewStatus!=CurrentUser.ReviewStatus.PENDING || review.status!=CurrentUser.ReviewStatus.PENDING) throw error(409,"STATE_CONFLICT","本次申请已审核，请重新核对");
        var status=dto.passed() ? CurrentUser.ReviewStatus.APPROVED:CurrentUser.ReviewStatus.REJECTED;
        var now=clock.instant().truncatedTo(ChronoUnit.MILLIS);
        int userChanged=users.update(null,new UpdateWrapper<UserEntity>().eq("id",account.id).eq("version",account.version)
                .set("review_status",status.name()).set("version",account.version+1).set("updated_at",now));
        int reviewChanged=reviews.update(null,new UpdateWrapper<RegistrationApplicationEntity>().eq("id",review.id).eq("version",review.version)
                .set("status",status.name()).set("reviewed_by",actor.id()).set("reviewed_at",now).set("reason",reason).set("version",review.version+1));
        if (userChanged!=1 || reviewChanged!=1) throw error(409,"VERSION_CONFLICT","资料已变化，请刷新核对");
        return record(actor.id(),account.id,review.id,"REVIEW_DECIDED","REVIEW_DECIDE",dto.operationId(),digest,account.version+1,reason);
    }
    @Transactional
    public TechnicianReviewVo.MutationReceiptVo resubmit(CurrentUser actor,ResubmitReviewDto dto) {
        var account=actor(actor,CurrentUser.Role.TECHNICIAN,true);
        String name=dto.name().trim();
        if (name.isBlank() || name.codePointCount(0,name.length())>100) throw error(400,"VALIDATION_FAILED","姓名必须为1—100字");
        byte[] digest=digest(Arrays.asList("REVIEW_RESUBMIT",account.id,dto.version(),dto.attempt(),dto.reviewVersion(),name,dto.phone()));
        var replay=replay(actor.id(),"REVIEW_RESUBMIT",dto.operationId(),account.id,digest); if (replay!=null) return replay;
        var current=reviews.lockCurrent(account.id,account.reviewAttempt);
        validateVersions(account,current,dto.version(),dto.attempt(),dto.reviewVersion());
        if (account.reviewStatus!=CurrentUser.ReviewStatus.REJECTED || current.status!=CurrentUser.ReviewStatus.REJECTED) throw error(409,"STATE_CONFLICT","只有审核未通过时才能重新申请");
        if (account.reviewAttempt==Integer.MAX_VALUE) throw error(409,"STATE_CONFLICT","申请次数已达上限，请联系维护者");
        if (users.selectCount(new QueryWrapper<UserEntity>().eq("phone",dto.phone()).ne("id",account.id))>0) throw error(409,"PHONE_EXISTS","手机号已被占用");
        var now=clock.instant().truncatedTo(ChronoUnit.MILLIS);
        try {
            if (users.update(null,new UpdateWrapper<UserEntity>().eq("id",account.id).eq("version",account.version)
                    .set("name",name).set("phone",dto.phone()).set("review_status","PENDING").set("review_attempt",account.reviewAttempt+1)
                    .set("version",account.version+1).set("updated_at",now))!=1) throw error(409,"VERSION_CONFLICT","资料已变化，请刷新核对");
        } catch (DuplicateKeyException error) { throw error(409,"PHONE_EXISTS","手机号已被占用"); }
        var review=new RegistrationApplicationEntity(); review.id=IdWorker.getId(); review.createdAt=now; review.submittedAt=now;
        review.userId=account.id; review.attempt=account.reviewAttempt+1; review.nameSnapshot=name; review.phoneSnapshot=dto.phone(); review.status=CurrentUser.ReviewStatus.PENDING;
        reviews.insert(review);
        return record(actor.id(),account.id,review.id,"REVIEW_RESUBMITTED","REVIEW_RESUBMIT",dto.operationId(),digest,account.version+1,null);
    }
    private TechnicianReviewVo detail(UserEntity u,boolean administrator) {
        var list=reviews.history(u.id); if (list.isEmpty()) throw error(404,"RESOURCE_NOT_FOUND","申请记录不存在");
        var actions=!u.enabled ? List.<String>of() : administrator && u.reviewStatus==CurrentUser.ReviewStatus.PENDING ? List.of("REVIEW_APPROVE","REVIEW_REJECT")
                : !administrator && u.reviewStatus==CurrentUser.ReviewStatus.REJECTED ? List.of("RESUBMIT_REVIEW") : List.<String>of();
        var history=list.stream().map(r -> {
            var reviewer=r.reviewedBy==null ? null:users.selectById(r.reviewedBy);
            return new TechnicianReviewVo.ReviewSubmissionVo(r.id.toString(),r.attempt,r.nameSnapshot,r.phoneSnapshot,CurrentUserVo.instant(r.submittedAt),r.status,r.version,
                    reviewer==null ? null:new TechnicianReviewVo.AccountReferenceVo(reviewer.id.toString(),reviewer.name),CurrentUserVo.instant(r.reviewedAt),r.reason);
        }).toList();
        return new TechnicianReviewVo(TechnicianReviewVo.AccountProfileVo.from(u,actions),history.getFirst(),history,actions,CurrentUserVo.instant(clock.instant()));
    }
    private UserEntity actor(CurrentUser actor,CurrentUser.Role role,boolean lock) {
        if (actor==null || actor.role()!=role) throw error(403,"FORBIDDEN","没有操作权限");
        var account=lock ? users.lock(actor.id()):users.selectById(actor.id()); requireActor(account,role); return account;
    }
    private static void requireActor(UserEntity account,CurrentUser.Role role) {
        if (account==null || !account.enabled) throw error(401,"ACCOUNT_DISABLED","登录账号已失效，请重新登录");
        if (account.role!=role) throw error(403,"FORBIDDEN","没有操作权限");
    }
    private static void technician(UserEntity account) {
        if (account==null || account.role!=CurrentUser.Role.TECHNICIAN) throw error(404,"RESOURCE_NOT_FOUND","维修申请不存在");
    }
    private static void validateVersions(UserEntity user,RegistrationApplicationEntity review,int version,int attempt,int reviewVersion) {
        if (review==null || user.version!=version || user.reviewAttempt!=attempt || review.version!=reviewVersion || user.version==Integer.MAX_VALUE || review.version==Integer.MAX_VALUE)
            throw error(409,"VERSION_CONFLICT","申请或账号资料已变化，请刷新核对");
    }
    private byte[] digest(Object fields) {
        try { return MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(fields)); }
        catch (Exception error) { throw new IllegalStateException("提交摘要生成失败"); }
    }
    private TechnicianReviewVo.MutationReceiptVo replay(long actor,String type,String operation,long target,byte[] digest) {
        var e=events.operation(actor,type,operation); if (e==null) return null;
        if (e.targetUserId!=target || !MessageDigest.isEqual(e.contentDigest,digest)) throw error(409,"OPERATION_CONTENT_MISMATCH","该操作标识的提交内容不一致");
        return TechnicianReviewVo.MutationReceiptVo.from(e);
    }
    private TechnicianReviewVo.MutationReceiptVo record(long actor,long target,long review,String eventType,String operationType,String operation,byte[] digest,int version,String reason) {
        var e=new AccountEventEntity(); e.id=IdWorker.getId(); e.createdAt=clock.instant().truncatedTo(ChronoUnit.MILLIS); e.occurredAt=e.createdAt;
        e.actorUserId=actor; e.targetUserId=target; e.reviewId=review; e.eventType=eventType; e.reason=reason;
        e.operationType=operationType; e.operationId=operation; e.contentDigest=digest; e.resultVersion=version; e.resultResourceId=target;
        events.insert(e); return TechnicianReviewVo.MutationReceiptVo.from(e);
    }
    private static BusinessException error(int status,String code,String message) { return new BusinessException(HttpStatus.valueOf(status),code,message); }
}
