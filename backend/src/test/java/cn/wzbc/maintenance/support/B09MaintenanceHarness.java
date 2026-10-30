package cn.wzbc.maintenance.support;
import cn.wzbc.maintenance.security.CurrentUser;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** Explicit real non-Web command check. Child output, including the one-time secret, stays in memory. */
class B09MaintenanceHarness extends B04HttpTestSupport {
    private String command(String...args)throws Exception{
        var jar=Path.of("target/campus-maintenance-0.1.0-SNAPSHOT.jar").toAbsolutePath();assertTrue(Files.isRegularFile(jar));
        var input=new ArrayList<>(List.of(Path.of(System.getProperty("java.home"),"bin/java.exe").toString(),"-Dstdout.encoding=UTF-8","-Dstderr.encoding=UTF-8","-jar",jar.toString(),"--spring.main.web-application-type=none","--spring.profiles.active=test","--maintenance.target-database=campus_maintenance_test","--campus.attachment-cleanup.enabled=false"));input.addAll(List.of(args));
        var builder=new ProcessBuilder(input).redirectErrorStream(true);builder.environment().put("CAMPUS_LOCAL_CONFIG","file:"+Path.of(System.getProperty("user.home"),".graduation-project/local-test.yml").toString().replace('\\','/'));
        var child=builder.start();var out=java.util.concurrent.CompletableFuture.supplyAsync(()->{try{return new String(child.getInputStream().readAllBytes(),StandardCharsets.UTF_8);}catch(Exception failure){throw new IllegalStateException("子命令输出读取失败");}});
        boolean exited=child.waitFor(25,TimeUnit.SECONDS);if(!exited){child.destroy();child.waitFor(10,TimeUnit.SECONDS);}assertTrue(exited,"本地维护命令应在完成后正常退出，不应留下调度线程");assertEquals(0,child.exitValue(),"本地非Web维护命令失败（秘密输出未写入日志）");return out.get(5,TimeUnit.SECONDS);
    }
    @Test void real_non_web_admin_reset_and_name_correction_preserve_identity()throws Exception{
        var admin=account(CurrentUser.Role.ADMIN,null);db.update("UPDATE user_account SET provision_key='campus-admin' WHERE id=?",admin.id);var old=browser();old.login(admin);long original=admin.id;
        String output=command("--maintenance.command=admin-reset");String secret=Arrays.stream(output.split("\\R")).filter(line->line.startsWith("ADMIN_RESET_PASSWORD=")).map(line->line.substring("ADMIN_RESET_PASSWORD=".length())).findFirst().orElseThrow(()->new AssertionError("缺少一次性结果"));
        assertEquals(original,users.provisionedAdmin().id);assertTrue(passwords.matches(secret,users.selectById(admin.id).passwordHash));assertEquals(401,old.get("/api/auth/me").statusCode());
        var fresh=browser();fresh.csrf();assertEquals(200,fresh.post("/api/auth/login",Map.of("loginName",admin.loginName,"password",secret)).statusCode());
        String rename=command("--maintenance.command=correct-location-name","--maintenance.location.id="+locationA,"--maintenance.location.version=0","--maintenance.location.name=101本地核验","--maintenance.operator-label=隔离维护核验","--maintenance.reason=B09专用命令核验");assertTrue(rename.contains("correct-location-name"));assertEquals("101本地核验",db.queryForObject("SELECT name FROM campus_location WHERE id=?",String.class,locationA));
        delete("location_name_history","location_id",locations);
    }
    @org.junit.jupiter.api.AfterEach void clearOwnNameHistory(){delete("location_name_history","location_id",locations);}
}
