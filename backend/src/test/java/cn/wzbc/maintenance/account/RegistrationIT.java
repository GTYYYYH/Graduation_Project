package cn.wzbc.maintenance.account;

import cn.wzbc.maintenance.Application;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest(classes=Application.class)
@AutoConfigureMockMvc(print=MockMvcPrint.NONE)
@Transactional
class RegistrationIT {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired ObjectMapper json;
    String login() { return "reg_"+UUID.randomUUID().toString().replace("-","").substring(0,14); }
    String phone() { return "13"+String.format("%09d",ThreadLocalRandom.current().nextLong(1_000_000_000L)); }
    Map<String,Object> input(String role,String account,String phone) {
        return new HashMap<>(Map.of("role",role,"loginName",account,"name","注册演示","phone",phone,"password","Synthetic1!","confirmPassword","Synthetic1!"));
    }
    org.springframework.mock.web.MockHttpServletResponse register(Map<String,Object> input) throws Exception {
        return mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json").content(json.writeValueAsString(input))).andReturn().getResponse();
    }
    @Test void duplicate_phone_is_rejected() throws Exception {
        String phone=phone();
        assertEquals(201,register(input("REPORTER",login(),phone)).getStatus());
        var duplicatePhoneResponse=register(input("TECHNICIAN",login(),phone));
        assertEquals(409,duplicatePhoneResponse.getStatus());
        assertEquals("PHONE_EXISTS",json.readTree(duplicatePhoneResponse.getContentAsString()).get("code").asString());
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM user_account WHERE phone=?",Integer.class,phone));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM registration_review WHERE phone_snapshot=?",Integer.class,phone));
    }
    @Test void account_case_is_globally_unique_including_disabled_users() throws Exception {
        String account=login();
        assertEquals(201,register(input("REPORTER",account,phone())).getStatus());
        db.update("UPDATE user_account SET enabled=0 WHERE login_name=?",account);
        var duplicate=register(input("TECHNICIAN",account.toUpperCase(Locale.ROOT),phone()));
        assertEquals(409,duplicate.getStatus());
        assertEquals("LOGIN_NAME_EXISTS",json.readTree(duplicate.getContentAsString()).get("code").asString());
    }
    @Test void registration_saves_hash_and_first_review_without_login_cookie() throws Exception {
        String account=login(),phone=phone();
        var response=register(input("TECHNICIAN",account,phone));
        assertEquals(201,response.getStatus());
        assertFalse(response.getHeaders("Set-Cookie").stream().anyMatch(c -> c.startsWith("CAMPUS_SESSION=") || c.startsWith("JSESSIONID=")));
        var body=json.readTree(response.getContentAsString()).get("data");
        assertTrue(body.get("id").isString()); assertEquals("PENDING",body.get("reviewStatus").asString());
        assertNotEquals("Synthetic1!",db.queryForObject("SELECT password_hash FROM user_account WHERE login_name=?",String.class,account));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM registration_review WHERE phone_snapshot=? AND status='PENDING' AND attempt=1",Integer.class,phone));
        assertFalse(response.getContentAsString().contains("password"));
    }
    @Test void boundaries_confirmation_roles_and_unknown_fields_are_validated() throws Exception {
        for (int length:new int[]{2,30}) {
            var value=input("REPORTER","中".repeat(length),phone());
            value.put("password",length==2 ? "A1234567" : "A1"+"密".repeat(30)); value.put("confirmPassword",value.get("password"));
            assertEquals(201,register(value).getStatus());
        }
        for (int length:new int[]{1,31}) assertEquals(400,register(input("REPORTER","中".repeat(length),phone())).getStatus());
        for (String password:new String[]{"Abc1234","A1"+"x".repeat(31),"abcdefgh","12345678"}) {
            var value=input("REPORTER",login(),phone()); value.put("password",password); value.put("confirmPassword",password);
            assertEquals(400,register(value).getStatus());
        }
        var mismatch=input("REPORTER",login(),phone()); mismatch.put("confirmPassword","OtherValue1");
        assertEquals(400,register(mismatch).getStatus());
        assertEquals(400,register(input("ADMIN",login(),phone())).getStatus());
        var missing=input("REPORTER",login(),phone()); missing.remove("role"); assertEquals(400,register(missing).getStatus());
        var unknown=input("REPORTER",login(),phone()); unknown.put("enabled",true); assertEquals(400,register(unknown).getStatus());
        assertEquals(400,register(input("REPORTER",login(),"12345678901")).getStatus());
    }
    @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void concurrent_same_phone_commits_one_account_and_no_orphan_review() throws Exception {
        String a=login(),b=login(),phone=phone();
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM user_account WHERE login_name IN (?,?) OR phone=?",Integer.class,a,b,phone));
        var ready=new CountDownLatch(2); var start=new CountDownLatch(1);
        try (var executor=Executors.newFixedThreadPool(2)) {
            var futures=new ArrayList<Future<Integer>>();
            for (String account:List.of(a,b)) futures.add(executor.submit(() -> { ready.countDown(); start.await(); return register(input("TECHNICIAN",account,phone)).getStatus(); }));
            assertTrue(ready.await(5,TimeUnit.SECONDS)); start.countDown();
            var statuses=List.of(futures.get(0).get(10,TimeUnit.SECONDS),futures.get(1).get(10,TimeUnit.SECONDS));
            assertEquals(1,statuses.stream().filter(s -> s==201).count()); assertEquals(1,statuses.stream().filter(s -> s==409).count());
            assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM user_account WHERE phone=?",Integer.class,phone));
            assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM registration_review WHERE phone_snapshot=?",Integer.class,phone));
        } finally {
            // 仅本测试事先确认不存在的两个精确账号；无全表删除。
            db.update("DELETE r FROM registration_review r JOIN user_account u ON r.user_id=u.id WHERE u.login_name IN (?,?)",a,b);
            db.update("DELETE FROM user_account WHERE login_name IN (?,?)",a,b);
        }
    }
}
