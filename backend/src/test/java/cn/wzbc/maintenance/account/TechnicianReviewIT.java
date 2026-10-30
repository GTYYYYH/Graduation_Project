package cn.wzbc.maintenance.account;

import cn.wzbc.maintenance.account.entity.UserEntity;
import cn.wzbc.maintenance.security.CurrentUser;
import cn.wzbc.maintenance.support.B02HttpTestSupport;
import java.net.http.HttpResponse;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import static org.junit.jupiter.api.Assertions.*;

class TechnicianReviewIT extends B02HttpTestSupport {
    private UserEntity technician() throws Exception {
        var b=browser(); b.csrf(); String name="tech_"+UUID.randomUUID().toString().replace("-","").substring(0,12);
        var r=b.post("/api/auth/register",Map.of("role","TECHNICIAN","loginName",name,"name","首次申请","phone",phone(),"password",PASSWORD,"confirmPassword",PASSWORD));
        assertEquals(201,r.statusCode()); long id=Long.parseLong(data(r).get("id").asString()); owned.add(id); return users.selectById(id);
    }
    private String path(long id) { return "/api/admin/accounts/"+id+"/registration-review"; }
    private Map<String,Object> decision(JsonNode detail,boolean passed,String reason) {
        var d=new HashMap<String,Object>(); d.put("operationId",UUID.randomUUID().toString()); d.put("version",detail.get("account").get("version").asInt());
        d.put("attempt",detail.get("current").get("attempt").asInt()); d.put("reviewVersion",detail.get("current").get("version").asInt());
        d.put("passed",passed); d.put("reason",reason); return d;
    }
    private Map<String,Object> resubmit(JsonNode detail,String name,String phone) {
        var d=decision(detail,false,null); d.remove("passed"); d.remove("reason"); d.put("name",name); d.put("phone",phone); return d;
    }
    @Test void stale_application_cannot_be_reviewed() throws Exception {
        var admin=account(CurrentUser.Role.ADMIN,null); var technician=technician(); var b=browser(); b.login(admin);
        var first=b.get(path(technician.id)); assertEquals(200,first.statusCode()); var input=decision(data(first),false,"材料需补充");
        assertEquals(200,b.post(path(technician.id),input).statusCode());
        var staleInput=decision(data(first),true,null); var staleReviewResponse=b.post(path(technician.id),staleInput);
        assertEquals(409,staleReviewResponse.statusCode()); assertEquals("VERSION_CONFLICT",code(staleReviewResponse));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM account_event WHERE target_user_id=?",Integer.class,technician.id));
    }
    @Test void rejection_resubmission_approval_preserves_original_account_and_snapshots() throws Exception {
        var admin=account(CurrentUser.Role.ADMIN,null); var t=technician(); var a=browser(); var b=browser(); a.login(admin); b.login(t);
        var pending=b.get("/api/account/review"); assertEquals(200,pending.statusCode());
        assertTrue(data(pending).get("allowedActions").isEmpty());
        var reject=decision(data(a.get(path(t.id))),false,"姓名资料需补充"); assertEquals(200,a.post(path(t.id),reject).statusCode());
        var rejected=data(b.get("/api/account/review")); assertEquals("REJECTED",rejected.get("current").get("status").asString());
        String newPhone=phone(); var input=resubmit(rejected,"第二轮资料",newPhone);
        assertEquals(200,b.post("/api/account/review/resubmit",input).statusCode());
        var second=data(a.get(path(t.id))); assertEquals(2,second.get("current").get("attempt").asInt());
        assertEquals(t.id.toString(),second.get("account").get("id").asString());
        assertEquals("姓名资料需补充",second.get("history").get(1).get("reason").asString());
        assertEquals("首次申请",second.get("history").get(1).get("nameSnapshot").asString());
        assertEquals(t.phone,second.get("history").get(1).get("phoneSnapshot").asString());
        assertEquals(200,a.post(path(t.id),decision(second,true,null)).statusCode());
        var me=data(b.get("/api/auth/me")).get("user"); assertEquals("APPROVED",me.get("reviewStatus").asString());
        assertTrue(me.get("capabilities").get("canWorkOnTasks").asBoolean());
        assertFalse(me.get("capabilities").get("canCreateRequest").asBoolean());
        assertEquals(newPhone,me.get("phone").asString()); assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM registration_review WHERE user_id=?",Integer.class,t.id));
    }
    @Test void disabled_readonly_blank_reason_and_wrong_role_are_rejected() throws Exception {
        var admin=account(CurrentUser.Role.ADMIN,null); var t=technician(); var a=browser(); var b=browser(); a.login(admin); b.login(t);
        var detail=data(a.get(path(t.id))); assertEquals(400,a.post(path(t.id),decision(detail,false,"  \t ")).statusCode());
        assertEquals(403,b.get(path(t.id)).statusCode());
        assertEquals(409,b.post("/api/account/review/resubmit",resubmit(data(b.get("/api/account/review")),"非法待审修改",phone())).statusCode());
        db.update("UPDATE user_account SET enabled=0,version=version+1 WHERE id=?",t.id);
        var disabled=a.get(path(t.id)); assertEquals(200,disabled.statusCode()); assertTrue(data(disabled).get("allowedActions").isEmpty());
        assertEquals(409,a.post(path(t.id),decision(data(disabled),true,null)).statusCode());
        assertEquals("PENDING",db.queryForObject("SELECT status FROM registration_review WHERE user_id=?",String.class,t.id));
    }
    @Test void same_operation_replays_receipt_and_changed_content_is_rejected() throws Exception {
        var admin=account(CurrentUser.Role.ADMIN,null); var t=technician(); var a=browser(); a.login(admin);
        var input=decision(data(a.get(path(t.id))),false,"重新核对"); var first=a.post(path(t.id),input); assertEquals(200,first.statusCode());
        var retry=a.post(path(t.id),input); assertEquals(200,retry.statusCode()); assertEquals(data(first),data(retry));
        input.put("reason","内容已改变"); assertEquals(409,a.post(path(t.id),input).statusCode());
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM account_event WHERE target_user_id=?",Integer.class,t.id));
    }
    @Test void duplicate_resubmission_phone_rolls_back_all_changes() throws Exception {
        var admin=account(CurrentUser.Role.ADMIN,null); var t=technician(); var other=account(CurrentUser.Role.REPORTER,null); var a=browser(); var b=browser(); a.login(admin); b.login(t);
        assertEquals(200,a.post(path(t.id),decision(data(a.get(path(t.id))),false,"资料补正")).statusCode());
        var input=resubmit(data(b.get("/api/account/review")),"不能保存的新姓名",other.phone);
        var r=b.post("/api/account/review/resubmit",input); assertEquals(409,r.statusCode()); assertEquals("PHONE_EXISTS",code(r));
        var unchanged=users.selectById(t.id); assertEquals("首次申请",unchanged.name); assertEquals(t.phone,unchanged.phone);
        assertEquals(1,unchanged.reviewAttempt); assertEquals(CurrentUser.ReviewStatus.REJECTED,unchanged.reviewStatus);
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM registration_review WHERE user_id=?",Integer.class,t.id));
    }
}
