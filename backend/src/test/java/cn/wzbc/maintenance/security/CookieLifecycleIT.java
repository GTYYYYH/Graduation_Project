package cn.wzbc.maintenance.security;

import cn.wzbc.maintenance.support.B02HttpTestSupport;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CookieLifecycleIT extends B02HttpTestSupport {
    @Test void real_cookie_session_expires_at_eight_hours_without_sliding() throws Exception {
        var user=account(CurrentUser.Role.REPORTER,null);var first=browser();var second=browser();var login=first.login(user);second.login(user);
        var cookie=login.headers().allValues("Set-Cookie").stream().filter(v->v.startsWith("CAMPUS_SESSION=")).findFirst().orElseThrow();assertTrue(cookie.contains("HttpOnly"));assertTrue(cookie.contains("SameSite=Lax"));assertTrue(cookie.contains("Path=/api"));
        var expiry=data(login).get("expiresAt").asString();assertEquals("2026-10-12T08:00:00.000Z",expiry);
        for(long seconds:List.of(60L,3600L,28799L)){time.set(Instant.parse("2026-10-12T00:00:00Z").plusSeconds(seconds));assertEquals(200,first.get("/api/auth/me").statusCode());}
        assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM login_session WHERE user_id=? AND expires_at=?",Integer.class,user.id,Instant.parse(expiry)));
        assertEquals(200,first.post("/api/auth/logout",null).statusCode());assertEquals(401,first.get("/api/auth/me").statusCode());assertEquals(200,second.get("/api/auth/me").statusCode());
        time.set(Instant.parse(expiry));var expired=second.get("/api/auth/me");assertEquals(401,expired.statusCode());assertEquals("SESSION_EXPIRED",code(expired));assertEquals("private, no-store",expired.headers().firstValue("Cache-Control").orElseThrow());
    }
    @Test void real_password_api_revokes_all_cookies_and_disable_enable_cannot_revive() throws Exception {
        var user=account(CurrentUser.Role.REPORTER,null);var a=browser();var b=browser();a.login(user);b.login(user);
        assertEquals(403,a.request("POST","/api/account/password",Map.of(),null).statusCode());assertEquals(200,a.get("/api/auth/me").statusCode());
        var changed=a.post("/api/account/password",Map.of("version",0,"operationId",UUID.randomUUID().toString(),"currentPassword",PASSWORD,"newPassword","NewSynthetic2!","confirmNewPassword","NewSynthetic2!"));assertEquals(200,changed.statusCode(),changed.body());
        assertEquals(401,a.get("/api/auth/me").statusCode());assertEquals(401,b.get("/api/auth/me").statusCode());assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM login_session WHERE user_id=? AND revoked_at IS NULL",Integer.class,user.id));
        a.csrf();assertEquals(401,a.post("/api/auth/login",Map.of("loginName",user.loginName,"password",PASSWORD)).statusCode());assertEquals(200,a.post("/api/auth/login",Map.of("loginName",user.loginName,"password","NewSynthetic2!")).statusCode());a.csrf();
        db.update("UPDATE user_account SET enabled=0 WHERE id=?",user.id);assertEquals(401,a.get("/api/auth/me").statusCode());db.update("UPDATE user_account SET enabled=1 WHERE id=?",user.id);assertEquals(401,a.get("/api/auth/me").statusCode());
    }
}
