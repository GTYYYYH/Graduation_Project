package cn.wzbc.maintenance.repairrequest;

import cn.wzbc.maintenance.account.entity.UserEntity;
import cn.wzbc.maintenance.security.CurrentUser;
import cn.wzbc.maintenance.support.*;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Scope is checked with genuine distinct Cookie accounts and registered real attachments. */
class RequestDetailProjectionIT extends B04HttpTestSupport {
    private B04Browser logged(UserEntity user) throws Exception { var b=b04Browser(); b.login(user); return b; }
    private long request(B04Browser b) throws Exception {
        var response=b.post("/api/repair-requests",createBody(upload(b),locationA,UUID.randomUUID().toString()));
        assertEquals(201,response.statusCode(),response.body()); return Long.parseLong(data(response).get("resourceId").asString());
    }
    private long order(long request,long technician,long admin,String status) {
        var fixture=new B04WorkOrderFixture(db,time.instant()); long device=fixture.equipment(locationA); equipment.add(device); return fixture.order(request,device,technician,admin,status);
    }

    @Test void current_assignee_can_read_original_request_but_unrelated_or_unqualified_accounts_cannot() throws Exception {
        var reporter=account(CurrentUser.Role.REPORTER,null); var admin=account(CurrentUser.Role.ADMIN,null); var worker=account(CurrentUser.Role.TECHNICIAN,CurrentUser.ReviewStatus.APPROVED);
        var reporting=logged(reporter); long request=request(reporting),order=order(request,worker.id,admin.id,"PENDING_ACCEPT"); String path="/api/repair-requests/"+request;
        var technician=logged(worker); var response=technician.get(path); assertEquals(200,response.statusCode(),response.body()); var detail=data(response);
        assertEquals("APPROVED",detail.get("status").asString()); assertEquals(Long.toString(order),detail.get("workOrder").get("id").asString()); assertEquals("演示故障原文",detail.get("currentSubmission").get("description").asString());
        assertTrue(detail.get("allowedActions").isEmpty(),"维修人员直接申请详情只读，工单入口已有独立路径");
        String image=detail.get("currentSubmission").get("attachments").get(0).get("id").asString(); assertEquals(200,technician.content(image).statusCode());
        assertEquals(200,reporting.get(path).statusCode()); assertEquals(200,logged(admin).get(path).statusCode());
        var unrelated=logged(account(CurrentUser.Role.TECHNICIAN,CurrentUser.ReviewStatus.APPROVED)); assertEquals(403,unrelated.get(path).statusCode()); assertEquals(403,unrelated.content(image).statusCode()); assertEquals(0,data(unrelated.get("/api/work-orders?pageSize=1")).get("total").asInt());
        assertEquals(403,logged(account(CurrentUser.Role.REPORTER,null)).get(path).statusCode());
        assertEquals(403,logged(account(CurrentUser.Role.TECHNICIAN,CurrentUser.ReviewStatus.PENDING)).get(path).statusCode()); assertEquals(401,b04Browser().get(path).statusCode());
        db.update("UPDATE user_account SET enabled=0 WHERE id=?",worker.id); assertEquals(401,technician.get(path).statusCode()); assertEquals(401,technician.content(image).statusCode());
    }

    @Test void former_assignee_request_projection_and_images_stop_at_visible_submission_and_relationship_end() throws Exception {
        var reporter=account(CurrentUser.Role.REPORTER,null); var admin=account(CurrentUser.Role.ADMIN,null); var former=account(CurrentUser.Role.TECHNICIAN,CurrentUser.ReviewStatus.APPROVED); var worker=account(CurrentUser.Role.TECHNICIAN,CurrentUser.ReviewStatus.APPROVED);
        var reporting=logged(reporter); long request=request(reporting); var original=data(reporting.get("/api/repair-requests/"+request)); long visible=Long.parseLong(original.get("currentSubmission").get("id").asString());
        String originalImage=original.get("currentSubmission").get("attachments").get(0).get("id").asString();
        time.set(time.instant().plusSeconds(20)); long order=order(request,worker.id,admin.id,"IN_PROGRESS"); new B04WorkOrderFixture(db,time.instant()).formerBeforeCurrent(order,former.id,admin.id,visible);
        time.set(time.instant().plusSeconds(20)); long later=IdWorker.getId(); Instant now=time.instant(); String marker="仅后续人员可见";
        // SQL creates future history only inside registered fixture IDs, without any B05 workflow endpoint.
        db.update("INSERT INTO repair_request_submission(id,created_at,request_id,submission_no,actor_user_id,equipment_name,equipment_category,address_text,description,contact_name,contact_phone,location_mode,location_id,location_name_snapshot,submitted_at,operation_type,operation_id,content_digest,result_version) SELECT ?,?,request_id,2,actor_user_id,?,equipment_category,?,?,?,'13900000000',location_mode,?,?,?,'SUPPLEMENT_REQUEST',?,?,2 FROM repair_request_submission WHERE id=?",later,now,marker+"设备",marker+"地址",marker+"描述",marker+"联系人",locationB,marker+"位置",now,UUID.randomUUID().toString(),MessageDigest.getInstance("SHA-256").digest(marker.getBytes(StandardCharsets.UTF_8)),visible);
        String laterImage=upload(reporting); db.update("UPDATE attachment_file SET state='BOUND',bound_submission_id=?,bound_at=? WHERE id=?",later,now,Long.parseLong(laterImage));
        db.update("UPDATE repair_request SET current_submission_id=?,equipment_name=?,address_text=?,description=?,contact_name=?,contact_phone='13900000000',location_id=?,location_name_snapshot=?,version=2,updated_at=? WHERE id=?",later,marker+"设备",marker+"地址",marker+"描述",marker+"联系人",locationB,marker+"位置",now,request);
        for(long submission:List.of(visible,later)) db.update("INSERT INTO repair_request_event(id,created_at,request_id,actor_user_id,submission_id,event_type,from_status,to_status,reason,occurred_at) VALUES(?,?,?,?,?,'SUPPLEMENTED','APPROVED','APPROVED',?,?)",IdWorker.getId(),now,request,reporter.id,submission,marker+"意见",now);
        var old=logged(former); var response=old.get("/api/repair-requests/"+request); assertEquals(200,response.statusCode(),response.body()); var limited=data(response);
        assertEquals(original.get("currentSubmission"),limited.get("currentSubmission")); assertEquals(1,limited.get("submissions").size()); assertEquals(1,limited.get("events").size()); assertEquals("演示空调",limited.get("equipmentName").asString()); assertEquals(Long.toString(locationA),limited.get("location").get("id").asString()); assertEquals(0,limited.get("version").asInt()); assertTrue(limited.get("allowedActions").isEmpty()); assertFalse(response.body().contains(marker));
        assertEquals(200,old.content(originalImage).statusCode()); assertEquals(403,old.content(laterImage).statusCode());
        var actual=logged(worker); var fullResponse=actual.get("/api/repair-requests/"+request); assertEquals(200,fullResponse.statusCode(),fullResponse.body()); var full=data(fullResponse);
        assertEquals(marker+"描述",full.get("currentSubmission").get("description").asString()); assertEquals(2,full.get("submissions").size()); assertEquals(3,full.get("events").size()); assertEquals(200,actual.content(originalImage).statusCode()); assertEquals(200,actual.content(laterImage).statusCode());
        assertEquals(2,data(reporting.get("/api/repair-requests/"+request)).get("submissions").size()); assertEquals(2,data(logged(admin).get("/api/repair-requests/"+request)).get("submissions").size());
        var history=data(old.get("/api/work-orders?scope=HISTORY&page=1&pageSize=1")); assertEquals(1,history.get("total").asInt()); assertEquals(1,history.get("records").size()); assertEquals(Long.toString(order),history.get("records").get(0).get("id").asString()); assertEquals(0,data(old.get("/api/work-orders?scope=CURRENT")).get("total").asInt());
        var current=data(actual.get("/api/work-orders?scope=CURRENT&page=1&pageSize=1")); assertEquals(1,current.get("total").asInt()); assertEquals(1,current.get("records").size());
    }

    @Test void completed_actual_technician_keeps_full_original_request_read_access() throws Exception {
        var reporter=account(CurrentUser.Role.REPORTER,null); var admin=account(CurrentUser.Role.ADMIN,null); var worker=account(CurrentUser.Role.TECHNICIAN,CurrentUser.ReviewStatus.APPROVED); var reporting=logged(reporter);
        long request=request(reporting); order(request,worker.id,admin.id,"COMPLETED"); var technician=logged(worker);
        var response=technician.get("/api/repair-requests/"+request); assertEquals(200,response.statusCode(),response.body()); var detail=data(response); assertEquals("COMPLETED",detail.get("workOrder").get("status").asString()); assertEquals(1,detail.get("submissions").size());
        assertEquals(200,technician.content(detail.get("currentSubmission").get("attachments").get(0).get("id").asString()).statusCode()); assertEquals(1,data(technician.get("/api/work-orders?scope=HISTORY&pageSize=1")).get("total").asInt()); assertEquals(0,data(technician.get("/api/work-orders?scope=CURRENT")).get("total").asInt());
    }
}
