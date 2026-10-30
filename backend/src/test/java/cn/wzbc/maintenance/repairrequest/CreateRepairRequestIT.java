package cn.wzbc.maintenance.repairrequest;
import cn.wzbc.maintenance.security.CurrentUser;
import cn.wzbc.maintenance.support.B04HttpTestSupport;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CreateRepairRequestIT extends B04HttpTestSupport {
    @Test void retry_returns_original_result() throws Exception {
        var reporter=account(CurrentUser.Role.REPORTER,null); var b=b04Browser(); b.login(reporter);
        String image=upload(b), operation=UUID.randomUUID().toString(); var body=createBody(image,locationA,operation);
        var first=b.post("/api/repair-requests",body); assertEquals(201,first.statusCode(),first.body());
        var retry=b.post("/api/repair-requests",body); String firstId=data(first).get("resourceId").asString(), retryId=data(retry).get("resourceId").asString();
        int requestCount=db.queryForObject("SELECT COUNT(*) FROM repair_request WHERE reporter_id=?",Integer.class,reporter.id);
        assertEquals(firstId,retryId); assertEquals(1,requestCount);
        assertEquals("PENDING_REVIEW",db.queryForObject("SELECT status FROM repair_request WHERE id=?",String.class,Long.valueOf(firstId)));
        assertEquals("BOUND",db.queryForObject("SELECT state FROM attachment_file WHERE id=?",String.class,Long.valueOf(image)));
        body.put("description","不同内容"); assertEquals(409,b.post("/api/repair-requests",body).statusCode());
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM repair_request_submission WHERE request_id=?",Integer.class,Long.valueOf(firstId)));
        var receipt=b.get("/api/operations/"+operation+"?operation=CREATE_REQUEST"); assertEquals("SUCCEEDED",data(receipt).get("kind").asString());
    }
    @Test void required_image_location_and_server_owned_fields_are_enforced() throws Exception {
        var b=b04Browser(); b.login(account(CurrentUser.Role.REPORTER,null)); String image=upload(b);
        var body=createBody(image,observation,UUID.randomUUID().toString()); assertEquals(400,b.post("/api/repair-requests",body).statusCode());
        body=createBody(image,locationA,UUID.randomUUID().toString()); body.put("attachmentIds",List.of()); assertEquals(400,b.post("/api/repair-requests",body).statusCode());
        body=createBody(image,locationA,UUID.randomUUID().toString()); body.put("locationMode","LEGACY_TEXT"); assertEquals(400,b.post("/api/repair-requests",body).statusCode());
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM repair_request WHERE reporter_id=?",Integer.class,owned.getFirst()));
    }
    @Test void optional_category_and_independent_contact_snapshot_are_preserved() throws Exception {
        var reporter=account(CurrentUser.Role.REPORTER,null); String originalPhone=reporter.phone; var b=b04Browser(); b.login(reporter);
        var body=createBody(upload(b),locationA,UUID.randomUUID().toString()); body.put("equipmentCategory",null);
        var created=b.post("/api/repair-requests",body); assertEquals(201,created.statusCode()); String id=data(created).get("resourceId").asString();
        var detail=data(b.get("/api/repair-requests/"+id)); var submission=detail.get("currentSubmission");
        assertTrue(submission.get("equipmentCategory").isNull()); assertEquals("用户原文地址",submission.get("addressText").asString());
        assertEquals("13800000000",submission.get("contactPhone").asString()); assertEquals(Long.toString(locationA),detail.get("location").get("id").asString());
        assertEquals(originalPhone,users.selectById(reporter.id).phone);
        assertTrue(detail.get("workOrder").isNull());
    }
    @Test void other_users_and_expired_images_cannot_bind_and_transaction_rolls_back() throws Exception {
        var a=b04Browser(); a.login(account(CurrentUser.Role.REPORTER,null)); String image=upload(a);
        var b=b04Browser(); b.login(account(CurrentUser.Role.REPORTER,null));
        assertEquals(403,b.post("/api/repair-requests",createBody(image,locationA,UUID.randomUUID().toString())).statusCode());
        db.update("UPDATE attachment_file SET expires_at=? WHERE id=?",time.instant().plusSeconds(1),Long.valueOf(image)); time.set(time.instant().plusSeconds(1));
        assertEquals(409,a.post("/api/repair-requests",createBody(image,locationA,UUID.randomUUID().toString())).statusCode());
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM repair_request WHERE reporter_id IN (?,?)",Integer.class,owned.get(0),owned.get(1)));
        assertEquals("TEMPORARY",db.queryForObject("SELECT state FROM attachment_file WHERE id=?",String.class,Long.valueOf(image)));
    }
    @Test void another_account_cannot_read_request_images_or_operation_result() throws Exception {
        var a=b04Browser(); a.login(account(CurrentUser.Role.REPORTER,null)); String image=upload(a), op=UUID.randomUUID().toString();
        var first=a.post("/api/repair-requests",createBody(image,locationA,op)); assertEquals(201,first.statusCode()); String id=data(first).get("resourceId").asString();
        var b=b04Browser(); b.login(account(CurrentUser.Role.REPORTER,null));
        assertEquals(403,b.get("/api/repair-requests/"+id).statusCode()); assertEquals(403,b.content(image).statusCode());
        var result=data(b.get("/api/operations/"+op+"?operation=CREATE_REQUEST")); assertEquals("NOT_CONFIRMED",result.get("kind").asString()); assertTrue(result.get("receipt").isNull());
        assertEquals(0,data(b.get("/api/repair-requests")).get("total").asInt());
        assertEquals(200,a.content(image).statusCode());
    }
    @Test void sixth_image_and_mixed_batches_are_rejected() throws Exception {
        var b=b04Browser(); b.login(account(CurrentUser.Role.REPORTER,null)); String batch=UUID.randomUUID().toString(); var ids=new ArrayList<String>();
        for(int i=0;i<6;i++) ids.add(data(b.upload(Map.of("operationId",UUID.randomUUID().toString(),"purpose","REQUEST_FAULT","batchId",batch),"fault.png","image/png",png(),true)).get("id").asString());
        var body=createBody(ids.getFirst(),locationA,UUID.randomUUID().toString()); body.put("attachmentIds",ids);
        assertEquals(400,b.post("/api/repair-requests",body).statusCode());
        body.put("operationId",UUID.randomUUID().toString()); body.put("attachmentIds",List.of(ids.getFirst(),upload(b)));
        assertEquals(409,b.post("/api/repair-requests",body).statusCode());
    }
    @Test void same_operation_concurrently_creates_only_one_request() throws Exception {
        var reporter=account(CurrentUser.Role.REPORTER,null); var a=b04Browser(); a.login(reporter); var b=b04Browser(); b.login(reporter);
        var body=createBody(upload(a),locationA,UUID.randomUUID().toString());
        try(var pool=Executors.newFixedThreadPool(2)) {
            var first=pool.submit(() -> a.post("/api/repair-requests",body)); var second=pool.submit(() -> b.post("/api/repair-requests",body));
            var r1=first.get(15,TimeUnit.SECONDS); var r2=second.get(15,TimeUnit.SECONDS);
            assertEquals(201,r1.statusCode()); assertEquals(201,r2.statusCode()); assertEquals(data(r1).get("resourceId").asString(),data(r2).get("resourceId").asString());
        }
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM repair_request WHERE reporter_id=?",Integer.class,reporter.id));
    }
}
