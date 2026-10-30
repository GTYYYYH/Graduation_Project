package cn.wzbc.maintenance.security;

import cn.wzbc.maintenance.support.B02HttpTestSupport;
import java.net.HttpCookie;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SessionSecurityIT extends B02HttpTestSupport {
    @org.springframework.beans.factory.annotation.Autowired cn.wzbc.maintenance.account.service.SessionService sessions;
    @org.springframework.beans.factory.annotation.Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @Test void fixed_eight_hour_expiry() throws Exception {
        var u=account(CurrentUser.Role.REPORTER,null); var browser=browser(); var login=browser.login(u);
        assertEquals("2026-10-12T08:00:00.000Z",data(login).get("expiresAt").asString());
        var beforeExpiry=db.queryForObject("SELECT expires_at FROM login_session WHERE user_id=?",java.time.LocalDateTime.class,u.id);
        time.set(Instant.parse("2026-10-12T07:59:59.999Z")); assertEquals(200,browser.get("/api/auth/me").statusCode());
        var expiryAfterMe=db.queryForObject("SELECT expires_at FROM login_session WHERE user_id=?",java.time.LocalDateTime.class,u.id);
        assertEquals(beforeExpiry,expiryAfterMe);
        time.set(Instant.parse("2026-10-12T08:00:00Z")); var meAtEightHours=browser.get("/api/auth/me");
        assertEquals(401,meAtEightHours.statusCode()); assertEquals("SESSION_EXPIRED",code(meAtEightHours));
    }
    @Test void real_cookie_flags_and_digest_only_storage() throws Exception {
        var u=account(CurrentUser.Role.REPORTER,null); var b=browser(); var login=b.login(u);
        String cookie=login.headers().allValues("Set-Cookie").stream().filter(c -> c.startsWith("CAMPUS_SESSION=")).findFirst().orElseThrow();
        assertTrue(cookie.contains("HttpOnly")); assertTrue(cookie.contains("SameSite=Lax")); assertTrue(cookie.contains("Path=/api"));
        assertFalse(cookie.toLowerCase().contains("secure")); assertFalse(cookie.contains("JSESSIONID"));
        String token=b.sessionToken(); assertTrue(token.length()>=43);
        byte[] digest=db.queryForObject("SELECT token_digest FROM login_session WHERE user_id=?",byte[].class,u.id);
        assertArrayEquals(java.security.MessageDigest.getInstance("SHA-256").digest(token.getBytes(java.nio.charset.StandardCharsets.US_ASCII)),digest);
        assertFalse(login.body().contains(token)); assertFalse(b.get("/api/auth/me").body().contains(token));
    }
    @Test void csrf_required_on_public_and_authenticated_writes_and_rotated_on_identity_change() throws Exception {
        var u=account(CurrentUser.Role.REPORTER,null); var b=browser(); b.csrf();
        String before=b.csrf;
        assertEquals(403,b.request("POST","/api/auth/login",Map.of("loginName",u.loginName,"password",PASSWORD),null).statusCode());
        assertEquals(403,b.request("POST","/api/auth/register",Map.of(),"wrong-token").statusCode());
        assertEquals(200,b.post("/api/auth/login",Map.of("loginName",u.loginName,"password",PASSWORD)).statusCode());
        assertEquals(403,b.request("POST","/api/auth/logout",null,before).statusCode());
        b.csrf(); assertTrue(b.csrfMatchesCookie(),"fresh masked CSRF matches cookie");
        assertEquals(403,b.request("POST","/api/auth/logout",null,null).statusCode());
        assertTrue(b.csrfMatchesCookie(),"missing header must not invalidate existing CSRF");
        assertEquals(200,b.get("/api/auth/me").statusCode());
        assertTrue(b.csrfMatchesCookie(),"me must not invalidate existing CSRF");
        assertEquals(200,b.post("/api/auth/logout",null).statusCode());
        assertEquals(403,b.request("POST","/api/auth/login",Map.of("loginName",u.loginName,"password",PASSWORD),b.csrf).statusCode());
    }
    @Test void logout_revokes_only_current_session_and_old_token_stays_invalid() throws Exception {
        var u=account(CurrentUser.Role.REPORTER,null); var a=browser(); var b=browser(); a.login(u); b.login(u);
        String old=a.sessionToken(); assertEquals(200,a.post("/api/auth/logout",null).statusCode());
        assertEquals(401,a.get("/api/auth/me").statusCode()); assertEquals(200,b.get("/api/auth/me").statusCode());
        var oldCookie=new HttpCookie("CAMPUS_SESSION",old); oldCookie.setVersion(0); oldCookie.setPath("/api"); a.cookies.getCookieStore().add(java.net.URI.create("http://127.0.0.1:"+port),oldCookie);
        assertEquals("SESSION_REVOKED",code(a.get("/api/auth/me")));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM login_session WHERE user_id=? AND revoked_at IS NOT NULL",Integer.class,u.id));
    }
    @Test void disabled_accounts_do_not_regain_old_sessions_and_permissions_do_not_destroy_login() throws Exception {
        var u=account(CurrentUser.Role.TECHNICIAN,CurrentUser.ReviewStatus.PENDING); var b=browser(); b.login(u);
        assertEquals(200,b.get("/api/campus/catalog").statusCode());
        assertFalse(data(b.get("/api/auth/me")).get("user").get("capabilities").get("canWorkOnTasks").asBoolean());
        assertEquals(403,b.get("/api/admin/accounts/1/registration-review").statusCode());
        assertEquals(200,b.get("/api/auth/me").statusCode());
        db.update("UPDATE user_account SET enabled=0 WHERE id=?",u.id);
        assertEquals(401,b.get("/api/auth/me").statusCode());
        db.update("UPDATE user_account SET enabled=1 WHERE id=?",u.id);
        assertEquals(401,b.get("/api/auth/me").statusCode());
    }
    @Test void password_transaction_revokes_all_old_sessions_and_new_hash_is_used() throws Exception {
        var u=account(CurrentUser.Role.REPORTER,null); var a=browser(); var b=browser(); a.login(u); b.login(u);
        new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(status -> {
            users.lock(u.id);
            db.update("UPDATE user_account SET password_hash=?,version=version+1 WHERE id=?",passwords.hash("Synthetic2!"),u.id);
            sessions.revokeAll(u.id);
        });
        assertEquals(401,a.get("/api/auth/me").statusCode()); assertEquals(401,b.get("/api/auth/me").statusCode());
        assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM login_session WHERE user_id=? AND revoke_reason='PASSWORD_CHANGED' AND revoked_at IS NOT NULL",Integer.class,u.id));
        a.csrf(); assertEquals(401,a.post("/api/auth/login",Map.of("loginName",u.loginName,"password",PASSWORD)).statusCode());
        assertEquals(200,a.post("/api/auth/login",Map.of("loginName",u.loginName,"password","Synthetic2!")).statusCode());
    }
    @Test void malformed_cookie_and_wrong_password_have_safe_json() throws Exception {
        var u=account(CurrentUser.Role.REPORTER,null); var b=browser(); b.csrf();
        var wrong=b.post("/api/auth/login",Map.of("loginName",u.loginName,"password","wrong-password"));
        assertEquals(401,wrong.statusCode()); assertEquals("AUTH_INVALID_CREDENTIALS",code(wrong));
        var c=new HttpCookie("CAMPUS_SESSION","malformed"); c.setVersion(0); c.setPath("/api"); b.cookies.getCookieStore().add(java.net.URI.create("http://127.0.0.1:"+port),c);
        var r=b.get("/api/auth/me"); assertEquals(401,r.statusCode()); assertFalse(r.body().contains("malformed"));
        assertEquals("private, no-store",r.headers().firstValue("Cache-Control").orElseThrow());
        assertEquals(r.headers().firstValue("X-Request-Id").orElseThrow(),json.readTree(r.body()).get("requestId").asString());
    }
}
