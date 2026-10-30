package cn.wzbc.maintenance.repairrequest;
import cn.wzbc.maintenance.security.CurrentUser;
import cn.wzbc.maintenance.support.B04HttpTestSupport;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RepairRequestTransitionIT extends B04HttpTestSupport {
    String create(B04Browser b) throws Exception { var r=b.post("/api/repair-requests",createBody(upload(b),locationA,UUID.randomUUID().toString())); assertEquals(201,r.statusCode()); return data(r).get("resourceId").asString(); }
    Map<String,Object> meta(int version) { return new HashMap<>(Map.of("operationId",UUID.randomUUID().toString(),"version",version)); }
    void returned(B04Browser admin,String id,int version,String reason) throws Exception { var dto=meta(version); dto.put("reason",reason); var r=admin.post("/api/repair-requests/"+id+"/return",dto); assertEquals(200,r.statusCode(),r.body()); }
    @Test void resubmit_preserves_first_submission() throws Exception {
        var b=b04Browser(); b.login(account(CurrentUser.Role.REPORTER,null)); var admin=b04Browser(); admin.login(account(CurrentUser.Role.ADMIN,null)); String id=create(b);
        var before=data(b.get("/api/repair-requests/"+id)); String firstSubmittedAt=before.get("firstSubmittedAt").asString(); String originalNo=before.get("requestNo").asString();
        returned(admin,id,0,"位置填写有误，请纠正原申请"); time.set(time.instant().plusSeconds(60));
        var dto=createBody("unused",locationB,UUID.randomUUID().toString()); dto.put("attachmentIds",List.of()); dto.put("version",1); dto.put("description","纠正后描述");
        var r=b.post("/api/repair-requests/"+id+"/supplement",dto); assertEquals(200,r.statusCode(),r.body());
        var after=data(b.get("/api/repair-requests/"+id)); String afterResubmitFirstSubmittedAt=after.get("firstSubmittedAt").asString(); assertEquals(firstSubmittedAt,afterResubmitFirstSubmittedAt);
        assertEquals(id,after.get("id").asString()); assertEquals(originalNo,after.get("requestNo").asString()); assertEquals(2,after.get("submissions").size());
        assertEquals(Long.toString(locationB),after.get("location").get("id").asString());
        assertEquals("演示故障原文",after.get("submissions").get(1).get("description").asString());
        String oldImage=after.get("submissions").get(1).get("attachments").get(0).get("id").asString(); assertEquals(200,b.content(oldImage).statusCode());
        assertEquals("PENDING_REVIEW",after.get("status").asString()); assertEquals(200,b.post("/api/repair-requests/"+id+"/supplement",dto).statusCode());
        assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM repair_request_submission WHERE request_id=?",Integer.class,Long.valueOf(id)));
    }
    @Test void return_requires_reason_and_old_version_cannot_overwrite() throws Exception {
        var b=b04Browser(); b.login(account(CurrentUser.Role.REPORTER,null)); var admin=b04Browser(); admin.login(account(CurrentUser.Role.ADMIN,null)); String id=create(b);
        var blank=meta(0); blank.put("reason","  "); assertEquals(400,admin.post("/api/repair-requests/"+id+"/return",blank).statusCode());
        returned(admin,id,0,"请补充图片说明"); var dto=createBody("unused",locationB,UUID.randomUUID().toString()); dto.put("attachmentIds",List.of()); dto.put("version",0);
        assertEquals(409,b.post("/api/repair-requests/"+id+"/supplement",dto).statusCode()); assertEquals(locationA,db.queryForObject("SELECT location_id FROM repair_request WHERE id=?",Long.class,Long.valueOf(id)));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM repair_request_submission WHERE request_id=?",Integer.class,Long.valueOf(id)));
    }
    @Test void only_original_reporter_can_withdraw_pending_or_returned_and_replay_is_safe() throws Exception {
        var b=b04Browser(); b.login(account(CurrentUser.Role.REPORTER,null)); var other=b04Browser(); other.login(account(CurrentUser.Role.REPORTER,null)); var admin=b04Browser(); admin.login(account(CurrentUser.Role.ADMIN,null));
        String pending=create(b); var dto=meta(0);
        assertEquals(403,other.post("/api/repair-requests/"+pending+"/withdraw",dto).statusCode()); assertEquals(403,admin.post("/api/repair-requests/"+pending+"/withdraw",dto).statusCode());
        assertEquals(200,b.post("/api/repair-requests/"+pending+"/withdraw",dto).statusCode()); assertEquals(200,b.post("/api/repair-requests/"+pending+"/withdraw",dto).statusCode());
        assertEquals("WITHDRAWN",data(b.get("/api/repair-requests/"+pending)).get("status").asString());
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM repair_request_event WHERE request_id=? AND event_type='WITHDRAWN'",Integer.class,Long.valueOf(pending)));
        String returned=create(b); returned(admin,returned,0,"超出范围：门窗不受理，请纠正或撤回");
        assertEquals(200,b.post("/api/repair-requests/"+returned+"/withdraw",meta(1)).statusCode());
    }
    @Test void supplement_upload_cannot_cross_request_and_invalid_binding_rolls_back() throws Exception {
        var b=b04Browser(); b.login(account(CurrentUser.Role.REPORTER,null)); var admin=b04Browser(); admin.login(account(CurrentUser.Role.ADMIN,null)); String a=create(b),c=create(b);
        returned(admin,a,0,"补充A"); returned(admin,c,0,"补充C");
        var fields=Map.of("operationId",UUID.randomUUID().toString(),"purpose","REQUEST_SUPPLEMENT","repairRequestId",a);
        var uploaded=b.upload(fields,"extra.png","image/png",png(),true); assertEquals(201,uploaded.statusCode()); String image=data(uploaded).get("id").asString();
        var dto=createBody(image,locationB,UUID.randomUUID().toString()); dto.put("version",1);
        assertEquals(409,b.post("/api/repair-requests/"+c+"/supplement",dto).statusCode());
        assertEquals("NEEDS_SUPPLEMENT",db.queryForObject("SELECT status FROM repair_request WHERE id=?",String.class,Long.valueOf(c)));
        assertEquals("TEMPORARY",db.queryForObject("SELECT state FROM attachment_file WHERE id=?",String.class,Long.valueOf(image)));
        assertEquals(200,b.post("/api/repair-requests/"+a+"/supplement",dto).statusCode()); assertEquals("BOUND",db.queryForObject("SELECT state FROM attachment_file WHERE id=?",String.class,Long.valueOf(image)));
    }
    @Test void legacy_supplement_keeps_null_location_and_client_cannot_bind_it() throws Exception {
        var b=b04Browser(); b.login(account(CurrentUser.Role.REPORTER,null)); var admin=b04Browser(); admin.login(account(CurrentUser.Role.ADMIN,null)); String id=create(b);
        // Isolated compatibility fixture, created by server-side SQL, never client input.
        db.update("UPDATE repair_request SET location_mode='LEGACY_TEXT',location_id=NULL,location_name_snapshot=NULL WHERE id=?",Long.valueOf(id));
        db.update("UPDATE repair_request_submission SET location_mode='LEGACY_TEXT',location_id=NULL,location_name_snapshot=NULL WHERE request_id=?",Long.valueOf(id));
        returned(admin,id,0,"请补充原文字资料"); var dto=createBody("unused",locationA,UUID.randomUUID().toString()); dto.put("version",1); dto.put("attachmentIds",List.of());
        assertEquals(400,b.post("/api/repair-requests/"+id+"/supplement",dto).statusCode()); dto.remove("locationId");
        assertEquals(200,b.post("/api/repair-requests/"+id+"/supplement",dto).statusCode()); var after=data(b.get("/api/repair-requests/"+id));
        assertEquals("LEGACY_TEXT",after.get("locationMode").asString()); assertTrue(after.get("location").isNull()); assertTrue(after.get("locationNameSnapshot").isNull());
    }
}
