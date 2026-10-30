package cn.wzbc.maintenance.support;

import cn.wzbc.maintenance.Application;
import cn.wzbc.maintenance.account.entity.UserEntity;
import cn.wzbc.maintenance.account.mapper.UserMapper;
import cn.wzbc.maintenance.common.MutableClock;
import cn.wzbc.maintenance.config.TestDatabaseGuard;
import cn.wzbc.maintenance.security.CurrentUser;
import cn.wzbc.maintenance.security.PasswordSupport;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import java.net.*;
import java.net.http.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes={Application.class,B02HttpTestSupport.TimeConfig.class},webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class B02HttpTestSupport {
    @Autowired protected JdbcTemplate db;
    @Autowired protected DataSource datasource;
    @Autowired protected UserMapper users;
    @Autowired protected PasswordSupport passwords;
    @Autowired protected ObjectMapper json;
    @Autowired protected MutableClock time;
    @LocalServerPort protected int port;
    protected final List<Long> owned=new ArrayList<>();
    protected static final String PASSWORD="Synthetic1!";
    @BeforeEach void verifyTargetAndResetClock() throws Exception {
        try (var connection=datasource.getConnection()) { TestDatabaseGuard.validateConnection(connection,"test"); }
        time.set(Instant.parse("2026-10-12T00:00:00Z"));
    }
    @AfterEach void cleanupOwnedOnly() throws Exception {
        try (var connection=datasource.getConnection()) { TestDatabaseGuard.validateConnection(connection,"test"); }
        for (long id:owned) {
            db.update("DELETE FROM account_event WHERE actor_user_id=? OR target_user_id=?",id,id);
        }
        for (long id:owned) db.update("DELETE FROM registration_review WHERE user_id=?",id);
        for (long id:owned) db.update("DELETE FROM login_session WHERE user_id=?",id);
        for (long id:owned) assertEquals(1,db.update("DELETE FROM user_account WHERE id=?",id));
        owned.clear();
    }
    protected UserEntity account(CurrentUser.Role role,CurrentUser.ReviewStatus review) {
        var u=new UserEntity(); u.id=IdWorker.getId(); u.createdAt=time.instant(); u.updatedAt=u.createdAt;
        u.loginName="http_"+UUID.randomUUID().toString().replace("-","").substring(0,14);
        u.name="HTTP演示"; u.phone=phone(); u.passwordHash=passwords.hash(PASSWORD);
        u.role=role; u.reviewStatus=review; u.reviewAttempt=role==CurrentUser.Role.TECHNICIAN ? 1:0; u.enabled=true;
        assertEquals(1,users.insert(u)); owned.add(u.id); return u;
    }
    protected String phone() { return "13"+String.format("%09d",ThreadLocalRandom.current().nextLong(1_000_000_000L)); }
    protected Browser browser() { return new Browser(); }
    protected JsonNode data(HttpResponse<String> r) {
        assertTrue(r.statusCode()==200 || r.statusCode()==201,"业务数据必须来自成功响应，实际HTTP="+r.statusCode());
        return json.readTree(r.body()).get("data");
    }
    protected String code(HttpResponse<String> r) { return json.readTree(r.body()).get("code").asString(); }
    protected class Browser {
        public final CookieManager cookies=new CookieManager(null,CookiePolicy.ACCEPT_ALL);
        final HttpClient client=HttpClient.newBuilder().cookieHandler(cookies).build();
        public String csrf;
        public HttpResponse<String> request(String method,String path,Object body,String csrfHeader) throws Exception {
            var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(java.time.Duration.ofSeconds(10));
            if (body!=null) b.header("Content-Type","application/json");
            if (csrfHeader!=null) b.header("X-CSRF-TOKEN",csrfHeader);
            b.method(method,body==null ? HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
            return client.send(b.build(),HttpResponse.BodyHandlers.ofString());
        }
        public void csrf() throws Exception {
            var r=request("GET","/api/auth/csrf",null,null); assertEquals(200,r.statusCode());
            csrf=data(r).get("token").asString();
        }
        public boolean csrfMatchesCookie() {
            byte[] mask=Base64.getUrlDecoder().decode(csrf); int half=mask.length/2; byte[] raw=new byte[half];
            for(int i=0;i<half;i++) raw[i]=(byte)(mask[i]^mask[i+half]);
            String decoded=new String(raw,java.nio.charset.StandardCharsets.UTF_8);
            return cookies.getCookieStore().getCookies().stream().filter(c -> c.getName().equals("XSRF-TOKEN")).anyMatch(c -> decoded.equals(c.getValue()));
        }
        public HttpResponse<String> login(UserEntity u) throws Exception {
            csrf(); var r=request("POST","/api/auth/login",Map.of("loginName",u.loginName,"password",PASSWORD),csrf);
            assertEquals(200,r.statusCode()); csrf(); return r;
        }
        public HttpResponse<String> get(String path) throws Exception { return request("GET",path,null,null); }
        public HttpResponse<String> post(String path,Object body) throws Exception { return request("POST",path,body,csrf); }
        public String sessionToken() { return cookies.getCookieStore().getCookies().stream().filter(c -> c.getName().equals("CAMPUS_SESSION")).findFirst().orElseThrow().getValue(); }
    }
    @TestConfiguration(proxyBeanMethods=false)
    public static class TimeConfig {
        @Bean @Primary MutableClock b02Clock() { return new MutableClock(Instant.parse("2026-10-12T00:00:00Z")); }
    }
}
