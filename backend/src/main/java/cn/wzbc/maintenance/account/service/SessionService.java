package cn.wzbc.maintenance.account.service;

import cn.wzbc.maintenance.account.dto.LoginDto;
import cn.wzbc.maintenance.account.entity.*;
import cn.wzbc.maintenance.account.mapper.*;
import cn.wzbc.maintenance.account.vo.CurrentUserVo;
import cn.wzbc.maintenance.common.BusinessException;
import cn.wzbc.maintenance.security.PasswordSupport;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SessionService {
    private final UserMapper users;
    private final SessionMapper sessions;
    private final PasswordSupport passwords;
    private final Clock clock;
    private final SecureRandom random=new SecureRandom();
    private final String unknownAccountHash;
    public SessionService(UserMapper users,SessionMapper sessions,PasswordSupport passwords,Clock clock) {
        this.users=users; this.sessions=sessions; this.passwords=passwords; this.clock=clock;
        this.unknownAccountHash=passwords.hash(passwords.randomValidPassword());
    }
    public record LoginResult(CurrentUserVo.AuthSessionVo response,String token) {}
    public record AuthenticatedSession(UserEntity user,SessionEntity session) {}

    @Transactional
    public LoginResult login(LoginDto dto) {
        var found=users.byLoginName(dto.loginName());
        if (found==null) { passwords.matches(dto.password(),unknownAccountHash); throw invalidCredentials(); }
        var u=users.lock(found.id);
        if (u==null || !passwords.matches(dto.password(),u.passwordHash)) throw invalidCredentials();
        if (!u.enabled) throw failure("ACCOUNT_DISABLED","账号已停用，请联系管理员");
        byte[] bytes=new byte[32]; random.nextBytes(bytes);
        String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        var now=clock.instant().truncatedTo(ChronoUnit.MILLIS);
        var session=new SessionEntity(); session.id=IdWorker.getId(); session.createdAt=now; session.userId=u.id;
        session.tokenDigest=digest(token); session.issuedAt=now; session.expiresAt=now.plus(Duration.ofHours(8));
        sessions.insert(session);
        return new LoginResult(new CurrentUserVo.AuthSessionVo(CurrentUserVo.from(u),CurrentUserVo.instant(session.expiresAt)),token);
    }
    @Transactional(noRollbackFor=BusinessException.class)
    public AuthenticatedSession authenticate(String token) {
        if (token==null || !token.matches("[A-Za-z0-9_-]{43}")) throw failure("AUTH_REQUIRED","请重新登录");
        var s=sessions.byDigest(digest(token));
        if (s==null) throw failure("AUTH_REQUIRED","请重新登录");
        if (s.revokedAt!=null) throw failure("SESSION_REVOKED","登录已失效，请重新登录");
        if (!clock.instant().isBefore(s.expiresAt)) throw failure("SESSION_EXPIRED","登录已到期，请重新登录");
        var u=users.selectById(s.userId);
        if (u==null || !u.enabled) {
            // 即使由本地显式维护停用，当前请求发现后也撤销全部旧会话，恢复不复活。
            users.lock(s.userId);
            sessions.revokeAll(s.userId,clock.instant().truncatedTo(ChronoUnit.MILLIS),"ACCOUNT_DISABLED");
            throw failure("ACCOUNT_DISABLED","账号已停用，请联系管理员");
        }
        return new AuthenticatedSession(u,s);
    }
    @Transactional
    public void revokeCurrent() {
        var authentication=SecurityContextHolder.getContext().getAuthentication();
        if (authentication!=null && authentication.getDetails() instanceof Long id) sessions.revokeCurrent(id,clock.instant().truncatedTo(ChronoUnit.MILLIS));
    }
    @Transactional
    public void revokeAll(long userId) { revokeAll(userId,"PASSWORD_CHANGED"); }
    @Transactional
    public void revokeAll(long userId,String reason) {
        if (!java.util.Set.of("PASSWORD_CHANGED","PASSWORD_RESET","ACCOUNT_DISABLED").contains(reason)) throw new IllegalArgumentException("非法撤销原因");
        users.lock(userId); sessions.revokeAll(userId,clock.instant().truncatedTo(ChronoUnit.MILLIS),reason);
    }
    public static byte[] digest(String token) {
        try { return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII)); }
        catch (NoSuchAlgorithmException error) { throw new IllegalStateException("SHA-256不可用"); }
    }
    private static BusinessException invalidCredentials() { return failure("AUTH_INVALID_CREDENTIALS","账号或密码错误"); }
    private static BusinessException failure(String code,String message) { return new BusinessException(HttpStatus.UNAUTHORIZED,code,message); }
}
