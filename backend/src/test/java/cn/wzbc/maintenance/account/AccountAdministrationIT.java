package cn.wzbc.maintenance.account;
import cn.wzbc.maintenance.security.CurrentUser;
import cn.wzbc.maintenance.support.B04HttpTestSupport;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class AccountAdministrationIT extends B04HttpTestSupport {
    private Map<String,Object> input(int version){return Map.of("operationId",UUID.randomUUID().toString(),"version",version);}
    @Test void personnel_fields_are_rejected_but_reset_dto_succeeds() throws Exception {
        var a=account(CurrentUser.Role.ADMIN,null);var r=account(CurrentUser.Role.REPORTER,null);
        var admin=browser();var old=browser();admin.login(a);old.login(r);
        String path="/api/admin/accounts/"+r.id+"/reset-password",hash=users.selectById(r.id).passwordHash;
        var dto=input(0);var expanded=new HashMap<String,Object>(dto);
        expanded.put("id",r.id.toString());expanded.put("name",r.name);expanded.put("role","REPORTER");
        var rejected=admin.post(path,expanded);
        assertEquals(400,rejected.statusCode());assertTrue(rejected.body().contains("VALIDATION_FAILED"));
        assertEquals(hash,users.selectById(r.id).passwordHash);assertEquals(0,users.selectById(r.id).version);
        assertEquals(200,old.get("/api/auth/me").statusCode());
        var accepted=admin.post(path,dto);assertEquals(200,accepted.statusCode());
        var result=data(accepted);assertEquals("NEW_SECRET",result.get("kind").asString());
        assertTrue(passwords.matches(result.get("newPassword").asString(),users.selectById(r.id).passwordHash));
        assertEquals(401,old.get("/api/auth/me").statusCode());
    }
    @Test void unfinished_business_blocks_disable() throws Exception {
        var a=account(CurrentUser.Role.ADMIN,null);var r=account(CurrentUser.Role.REPORTER,null);var admin=browser();var reporter=b04Browser();admin.login(a);reporter.login(r);
        var created=reporter.post("/api/repair-requests",createBody(upload(reporter),locationA,UUID.randomUUID().toString()));assertEquals(201,created.statusCode());
        var disableWithUnfinishedBusinessResponse=admin.post("/api/admin/accounts/"+r.id+"/disable",input(0));assertEquals(409,disableWithUnfinishedBusinessResponse.statusCode());
        assertTrue(disableWithUnfinishedBusinessResponse.body().contains("RR-"));assertTrue(users.selectById(r.id).enabled);assertEquals(200,reporter.get("/api/auth/me").statusCode());
    }
    @Test void disable_enable_preserves_qualification_and_never_revives_cookie() throws Exception {
        var a=account(CurrentUser.Role.ADMIN,null);var t=account(CurrentUser.Role.TECHNICIAN,CurrentUser.ReviewStatus.PENDING);var admin=browser();var one=browser();var two=browser();admin.login(a);one.login(t);two.login(t);
        assertEquals(200,admin.post("/api/admin/accounts/"+t.id+"/disable",input(0)).statusCode());assertEquals(401,one.get("/api/auth/me").statusCode());
        assertEquals(200,admin.post("/api/admin/accounts/"+t.id+"/enable",input(1)).statusCode());assertEquals(401,two.get("/api/auth/me").statusCode());assertEquals(CurrentUser.ReviewStatus.PENDING,users.selectById(t.id).reviewStatus);
        assertEquals(409,admin.post("/api/admin/accounts/"+a.id+"/disable",input(0)).statusCode());
        assertEquals(403,admin.post("/api/admin/accounts/"+a.id+"/reset-password",input(0)).statusCode());
    }
    @Test void random_secret_is_returned_once_replay_contains_no_secret_and_revokes_all_sessions() throws Exception {
        var a=account(CurrentUser.Role.ADMIN,null);var r=account(CurrentUser.Role.REPORTER,null);var admin=browser();var old=browser();admin.login(a);old.login(r);
        var input=input(0);String path="/api/admin/accounts/"+r.id+"/reset-password";var first=data(admin.post(path,input));assertEquals("NEW_SECRET",first.get("kind").asString());
        String secret=first.get("newPassword").asString();assertTrue(secret.matches("(?s)(?=.*[A-Za-z])(?=.*[0-9]).{8,32}"));assertTrue(passwords.matches(secret,users.selectById(r.id).passwordHash));assertEquals(401,old.get("/api/auth/me").statusCode());
        var replay=data(admin.post(path,input));assertEquals("ALREADY_COMMITTED",replay.get("kind").asString());assertTrue(replay.get("newPassword").isNull());
        var again=data(admin.post(path,input(1)));assertFalse(passwords.matches(secret,users.selectById(r.id).passwordHash));assertNotEquals(secret,again.get("newPassword").asString());
    }
}
