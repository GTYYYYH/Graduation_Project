package cn.wzbc.maintenance.account;
import cn.wzbc.maintenance.account.mapper.SessionMapper;
import cn.wzbc.maintenance.security.CurrentUser;
import cn.wzbc.maintenance.support.B04HttpTestSupport;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class AccountAtomicityIT extends B04HttpTestSupport {
    @MockitoSpyBean SessionMapper sessions;
    @Test void revoke_failure_rolls_back_password_disable_and_reset() throws Exception {
        var a=account(CurrentUser.Role.ADMIN,null);var r=account(CurrentUser.Role.REPORTER,null);var admin=browser();var owner=browser();admin.login(a);owner.login(r);
        for(String action:List.of("password","disable","reset-password")){
            reset(sessions);doThrow(new IllegalStateException("B09 isolated revoke failure")).when(sessions).revokeAll(eq(r.id.longValue()),any(),anyString());
            Map<String,Object> body=new HashMap<>(Map.of("operationId",UUID.randomUUID().toString(),"version",0));if(action.equals("password"))body.putAll(Map.of("currentPassword",PASSWORD,"newPassword","NewSynthetic2!","confirmNewPassword","NewSynthetic2!"));
            var response=action.equals("password")?owner.post("/api/account/password",body):admin.post("/api/admin/accounts/"+r.id+"/"+action,body);assertEquals(500,response.statusCode());
            var saved=users.selectById(r.id);assertEquals(r.passwordHash,saved.passwordHash);assertTrue(saved.enabled);assertEquals(0,saved.version);assertEquals(200,owner.get("/api/auth/me").statusCode());assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM account_event WHERE target_user_id=?",Integer.class,r.id));
        }
        reset(sessions);
    }
    @Test void disable_and_create_share_user_lock_without_missed_unfinished_business() throws Exception {
        var a=account(CurrentUser.Role.ADMIN,null);var r=account(CurrentUser.Role.REPORTER,null);var admin=browser();var owner=b04Browser();admin.login(a);owner.login(r);var body=createBody(upload(owner),locationA,UUID.randomUUID().toString());var latch=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)){
            var create=pool.submit(()->{latch.await();return owner.post("/api/repair-requests",body).statusCode();});
            var disable=pool.submit(()->{latch.await();return admin.post("/api/admin/accounts/"+r.id+"/disable",Map.of("version",0,"operationId",UUID.randomUUID().toString())).statusCode();});latch.countDown();int c=create.get(15,TimeUnit.SECONDS),d=disable.get(15,TimeUnit.SECONDS);
            assertTrue(c==201&&d==409||c==401&&d==200,"create or disable must be the sole consistent winner");assertEquals(c==201,users.selectById(r.id).enabled);assertEquals(c==201?1:0,db.queryForObject("SELECT COUNT(*) FROM repair_request WHERE reporter_id=?",Integer.class,r.id));
        }
    }
    @Test void concurrently_saving_same_phone_keeps_unique_identity() throws Exception {
        var first=account(CurrentUser.Role.REPORTER,null);var second=account(CurrentUser.Role.REPORTER,null);var one=browser();var two=browser();one.login(first);two.login(second);String shared=phone();var latch=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)){
            var a=pool.submit(()->{latch.await();return one.request("PATCH","/api/account/profile",Map.of("version",0,"operationId",UUID.randomUUID().toString(),"name","合成一","phone",shared),one.csrf).statusCode();});
            var b=pool.submit(()->{latch.await();return two.request("PATCH","/api/account/profile",Map.of("version",0,"operationId",UUID.randomUUID().toString(),"name","合成二","phone",shared),two.csrf).statusCode();});latch.countDown();assertEquals(Set.of(200,409),Set.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS)));assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM user_account WHERE phone=?",Integer.class,shared));
        }
    }
}
