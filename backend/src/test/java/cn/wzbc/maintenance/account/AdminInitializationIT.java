package cn.wzbc.maintenance.account;

import cn.wzbc.maintenance.Application;
import cn.wzbc.maintenance.account.service.AdminInitializationService;
import cn.wzbc.maintenance.security.PasswordSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes=Application.class)
@Transactional
class AdminInitializationIT {
    @Autowired AdminInitializationService service;
    @Autowired PasswordSupport passwords;
    @Autowired JdbcTemplate db;

    @Test void second_init_preserves_identity() {
        var first=service.initialize("演示管理员","演示管理员","13900000001");
        assertTrue(first.changed());
        Long firstUserId=first.userId();
        String firstHash=db.queryForObject("SELECT password_hash FROM user_account WHERE id=?",String.class,firstUserId);
        assertNotEquals(first.newPassword(),firstHash);
        assertTrue(passwords.matches(first.newPassword(),firstHash));
        var second=service.initialize("另一名称","不覆盖","13900000002");
        Long secondUserId=second.userId();
        String secondHash=db.queryForObject("SELECT password_hash FROM user_account WHERE id=?",String.class,secondUserId);
        assertEquals(firstUserId,secondUserId); assertEquals(firstHash,secondHash);
        assertFalse(second.changed()); assertNull(second.newPassword());
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM user_account WHERE role='ADMIN'",Integer.class));
    }
    @Test void random_password_is_valid_and_hash_supports_unicode_boundary() {
        String a=passwords.randomValidPassword(), b=passwords.randomValidPassword();
        assertTrue(a.matches("(?s)(?=.*[A-Za-z])(?=.*[0-9]).{8,32}"));
        assertNotEquals(a,b);
        String unicode="A1"+"密".repeat(30);
        String hash=passwords.hash(unicode);
        assertTrue(passwords.matches(unicode,hash)); assertFalse(passwords.matches(unicode+"a",hash));
        assertNotEquals(unicode,hash);
    }
    @Test void normal_start_does_not_provision_accounts() {
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM user_account WHERE provision_key IS NOT NULL",Integer.class));
    }
    @Test void non_web_mode_starts_without_http_security_dependencies_or_auto_initialization() {
        var app=new org.springframework.boot.SpringApplication(Application.class);
        app.setWebApplicationType(org.springframework.boot.WebApplicationType.NONE);
        try (var context=app.run("--spring.main.web-application-type=none")) {
            assertFalse(context instanceof org.springframework.web.context.WebApplicationContext);
            assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM user_account WHERE provision_key IS NOT NULL",Integer.class));
        }
    }
}
