package cn.wzbc.maintenance.account;

import cn.wzbc.maintenance.security.CurrentUser;
import cn.wzbc.maintenance.support.B02HttpTestSupport;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AccountSettingsIT extends B02HttpTestSupport {
    private Map<String,Object> profile(int version,String name,String phone) { return new HashMap<>(Map.of("version",version,"operationId",UUID.randomUUID().toString(),"name",name,"phone",phone)); }
    private Map<String,Object> password(int version,String old,String next) { return new HashMap<>(Map.of("version",version,"operationId",UUID.randomUUID().toString(),"currentPassword",old,"newPassword",next,"confirmNewPassword",next)); }
    @Test void unicode_limits_match_registration_codepoint_rules() throws Exception {
        var r=account(CurrentUser.Role.REPORTER,null);var b=browser();b.login(r);String next="A1"+"😀".repeat(30);
        assertEquals(400,b.post("/api/account/password",password(0,PASSWORD,next+"X")).statusCode());
        assertEquals(200,b.request("PATCH","/api/account/profile",profile(0,"😀".repeat(100),r.phone),b.csrf).statusCode());
        assertEquals(200,b.post("/api/account/password",password(1,PASSWORD,next)).statusCode());assertTrue(passwords.matches(next,users.selectById(r.id).passwordHash));
    }
    @Test void approved_profile_change_keeps_review_snapshot() throws Exception {
        var t=account(CurrentUser.Role.TECHNICIAN,CurrentUser.ReviewStatus.APPROVED);
        var admin=account(CurrentUser.Role.ADMIN,null);
        db.update("INSERT INTO registration_review(id,created_at,user_id,attempt,name_snapshot,phone_snapshot,submitted_at,status,reviewed_by,reviewed_at) VALUES(?,?,?,1,?,?,?,'APPROVED',?,?)",com.baomidou.mybatisplus.core.toolkit.IdWorker.getId(),time.instant(),t.id,t.name,t.phone,time.instant(),admin.id,time.instant());
        var b=browser(); b.login(t); String originalSnapshotPhone=t.phone;
        var input=profile(0,"更新姓名",phone());
        assertEquals(200,b.request("PATCH","/api/account/profile",input,b.csrf).statusCode());
        String savedSnapshotPhone=db.queryForObject("SELECT phone_snapshot FROM registration_review WHERE user_id=?",String.class,t.id);
        assertEquals(originalSnapshotPhone,savedSnapshotPhone);
        assertEquals(input.get("phone"),users.selectById(t.id).phone);
        assertEquals(CurrentUser.ReviewStatus.APPROVED,users.selectById(t.id).reviewStatus);
        assertEquals(200,b.request("PATCH","/api/account/profile",input,b.csrf).statusCode());
        input.put("name","改变重放内容"); assertEquals(409,b.request("PATCH","/api/account/profile",input,b.csrf).statusCode());
    }
    @Test void pending_rejected_and_duplicate_phone_cannot_change_profile() throws Exception {
        for(var status:List.of(CurrentUser.ReviewStatus.PENDING,CurrentUser.ReviewStatus.REJECTED)) {
            var t=account(CurrentUser.Role.TECHNICIAN,status); var b=browser(); b.login(t);
            assertEquals(200,b.get("/api/account/profile").statusCode());
            assertEquals(409,b.request("PATCH","/api/account/profile",profile(0,"非法修改",phone()),b.csrf).statusCode());
            assertEquals(0,users.selectById(t.id).version);
        }
        var r=account(CurrentUser.Role.REPORTER,null); var other=account(CurrentUser.Role.REPORTER,null); var b=browser();b.login(r);
        assertEquals(409,b.request("PATCH","/api/account/profile",profile(0,"不能保存",other.phone),b.csrf).statusCode());
        assertEquals(r.phone,users.selectById(r.id).phone);
        assertEquals(400,b.request("PATCH","/api/account/profile",profile(0,"", "123"),b.csrf).statusCode());
    }
    @Test void password_change_revokes_all_sessions_and_replays_without_changing_hash() throws Exception {
        var r=account(CurrentUser.Role.REPORTER,null); var b=browser(); var second=browser(); b.login(r);second.login(r);
        var bad=password(0,"wrong","NewSynthetic2!"); assertEquals(400,b.post("/api/account/password",bad).statusCode());
        assertEquals(r.passwordHash,users.selectById(r.id).passwordHash);assertEquals(200,second.get("/api/auth/me").statusCode());
        var input=password(0,PASSWORD,"NewSynthetic2!");var changed=b.post("/api/account/password",input);assertEquals(200,changed.statusCode());
        assertTrue(changed.headers().allValues("Set-Cookie").stream().anyMatch(cookie->cookie.startsWith("CAMPUS_SESSION=")&&cookie.contains("Max-Age=0")&&cookie.contains("HttpOnly")&&cookie.contains("Path=/api")));
        assertEquals(401,second.get("/api/auth/me").statusCode());assertEquals(401,b.get("/api/auth/me").statusCode());
        var saved=users.selectById(r.id);assertTrue(passwords.matches("NewSynthetic2!",saved.passwordHash));assertFalse(passwords.matches(PASSWORD,saved.passwordHash));
        var fresh=browser();fresh.csrf();assertEquals(200,fresh.post("/api/auth/login",Map.of("loginName",r.loginName,"password","NewSynthetic2!")).statusCode());fresh.csrf();
        assertEquals(200,fresh.post("/api/account/password",input).statusCode());assertEquals(saved.passwordHash,users.selectById(r.id).passwordHash);
        input.put("newPassword","Different3!");input.put("confirmNewPassword","Different3!");assertEquals(409,fresh.post("/api/account/password",input).statusCode());
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM account_event WHERE target_user_id=? AND event_type='PASSWORD_CHANGED'",Integer.class,r.id));
    }
}
