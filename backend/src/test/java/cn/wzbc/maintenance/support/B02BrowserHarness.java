package cn.wzbc.maintenance.support;

import cn.wzbc.maintenance.Application;
import cn.wzbc.maintenance.account.dto.RegisterDto;
import cn.wzbc.maintenance.account.service.RegistrationService;
import cn.wzbc.maintenance.campus.service.CampusInitializationService;
import cn.wzbc.maintenance.security.CurrentUser;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import static org.junit.jupiter.api.Assertions.*;

// 仅显式-Dtest=B02BrowserHarness执行；不匹配普通*Test/*IT，不增加生产端点。
@SpringBootTest(classes={Application.class,B02HttpTestSupport.TimeConfig.class},webEnvironment=SpringBootTest.WebEnvironment.DEFINED_PORT,properties="server.port=8081")
class B02BrowserHarness extends B02HttpTestSupport {
    @Autowired CampusInitializationService campus;
    @Autowired RegistrationService registration;
    @Test void isolated_browser_fixture_lifecycle() throws Exception {
        Path root=Path.of("..").toAbsolutePath().normalize();
        Path attachments=root.resolve("data/attachments/test").toRealPath();
        Path control=Files.createDirectory(attachments.resolve("b02-browser-"+UUID.randomUUID()));
        assertEquals(attachments,control.toRealPath().getParent());
        var tables=List.of("campus_initialization","equipment","campus_location","campus_floor","campus_building");
        var ownedCampus=new LinkedHashMap<String,List<Long>>();
        for(String table:tables) assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM "+table,Integer.class));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM user_account",Integer.class));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM user_account WHERE login_name='b02_ui_new'",Integer.class));
        try {
            assertTrue(campus.initialize(root.resolve("config/campus.json")));
            for(String table:tables) ownedCampus.put(table,db.queryForList("SELECT id FROM "+table,Long.class));
            var admin=account(CurrentUser.Role.ADMIN,null);
            db.update("UPDATE user_account SET login_name='b02_ui_admin',name='浏览器演示管理员',phone='13909990001' WHERE id=?",admin.id);
            for(var entry:List.of(new String[]{"REPORTER","b02_ui_reporter","13909990002","浏览器演示报修人"},new String[]{"TECHNICIAN","b02_ui_tech","13909990003","浏览器演示维修人员"})) {
                var result=registration.register(new RegisterDto(entry[0],entry[1],entry[3],entry[2],PASSWORD,PASSWORD));
                owned.add(Long.parseLong(result.id()));
            }
            Files.writeString(root.resolve("tests/evidence/B02-browser-control.json"),json.writeValueAsString(Map.of("profile","test","database","campus_maintenance_test","backendPort",port,"controlDirectory",control.toString(),"ownedCampusIds",ownedCampus,"ownedAccountIds",List.copyOf(owned))));
            Files.writeString(control.resolve("ready"),"ready",StandardOpenOption.CREATE_NEW);
            long deadline=System.nanoTime()+Duration.ofMinutes(20).toNanos();
            while(!Files.exists(control.resolve("done")) && System.nanoTime()<deadline) Thread.sleep(250);
            assertTrue(Files.exists(control.resolve("done")),"浏览器验证等待超时");
            var extra=users.byLoginName("b02_ui_new");
            if(extra!=null) {
                assertEquals("13909990004",extra.phone); assertEquals(CurrentUser.Role.REPORTER,extra.role);
                assertEquals(time.instant(),extra.createdAt); owned.add(extra.id);
            }
        } finally {
            try(var connection=datasource.getConnection()) { cn.wzbc.maintenance.config.TestDatabaseGuard.validateConnection(connection,"test"); }
            for(var table:tables) for(Long id:ownedCampus.getOrDefault(table,List.of())) assertEquals(1,db.update("DELETE FROM "+table+" WHERE id=?",id));
            for(String file:List.of("ready","done")) { Path path=control.resolve(file); if(Files.exists(path)) { assertEquals(control.toRealPath(),path.toRealPath().getParent()); Files.delete(path); } }
            assertEquals(attachments,control.toRealPath().getParent()); Files.delete(control);
        }
    }
}
