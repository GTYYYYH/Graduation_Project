package cn.wzbc.maintenance.account;
import cn.wzbc.maintenance.security.CurrentUser;
import cn.wzbc.maintenance.support.B02HttpTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import cn.wzbc.maintenance.account.service.AdminPasswordMaintenanceService;
import cn.wzbc.maintenance.account.service.AdminInitializationService;
import static org.junit.jupiter.api.Assertions.*;
class AdminResetIT extends B02HttpTestSupport {
    @Autowired AdminPasswordMaintenanceService maintenance;
    @Autowired AdminInitializationService initialization;
    @Test void local_reset_keeps_original_identity() throws Exception {
        var a=account(CurrentUser.Role.ADMIN,null);db.update("UPDATE user_account SET provision_key='campus-admin' WHERE id=?",a.id);var b=browser();b.login(a);long originalAdminId=a.id;
        var result=maintenance.reset();long adminIdAfterReset=result.userId();assertEquals(originalAdminId,adminIdAfterReset);
        assertTrue(passwords.matches(result.newPassword(),users.selectById(a.id).passwordHash));assertFalse(passwords.matches(PASSWORD,users.selectById(a.id).passwordHash));assertEquals(401,b.get("/api/auth/me").statusCode());
        var skipped=initialization.initialize(null,null,null);assertFalse(skipped.changed());assertEquals(originalAdminId,skipped.userId());assertNull(skipped.newPassword());
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM account_event WHERE target_user_id=? AND event_type='PASSWORD_RESET'",Integer.class,a.id));
    }
}
