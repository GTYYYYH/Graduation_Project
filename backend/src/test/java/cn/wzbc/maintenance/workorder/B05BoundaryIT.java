package cn.wzbc.maintenance.workorder;

import cn.wzbc.maintenance.account.entity.UserEntity;
import cn.wzbc.maintenance.security.CurrentUser;
import cn.wzbc.maintenance.support.B04HttpTestSupport;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Real Cookie/CSRF HTTP, independent MySQL transactions and real attachment files. */
class B05BoundaryIT extends B04HttpTestSupport {
    private String op() { return UUID.randomUUID().toString(); }
    private record Flow(long order, UserEntity worker, B04Browser owner, B04Browser admin, B04Browser tech) {}
    private Flow flow(boolean accepted) throws Exception {
        var reporter=account(CurrentUser.Role.REPORTER,null); var admin=account(CurrentUser.Role.ADMIN,null); var worker=account(CurrentUser.Role.TECHNICIAN,CurrentUser.ReviewStatus.APPROVED);
        var owner=b04Browser(); owner.login(reporter); var manager=b04Browser(); manager.login(admin); var tech=b04Browser(); tech.login(worker);
        var created=owner.post("/api/repair-requests",createBody(upload(owner),locationA,op())); assertEquals(201,created.statusCode(),created.body());
        String request=data(created).get("resourceId").asString();
        var assigned=manager.post("/api/repair-requests/"+request+"/approve-and-assign",Map.of("version",0,"technicianId",worker.id.toString(),"confirmPreviouslyRefused",false,"operationId",op()));
        assertEquals(200,assigned.statusCode(),assigned.body()); long order=Long.parseLong(data(assigned).get("relatedResourceId").asString());
        if(accepted) assertEquals(200,tech.post("/api/work-orders/"+order+"/accept",Map.of("version",0,"operationId",op())).statusCode());
        return new Flow(order,worker,owner,manager,tech);
    }
    private String voucher(Flow f) throws Exception {
        var result=f.tech.upload(Map.of("operationId",op(),"purpose","REPAIR_VOUCHER","workOrderId",Long.toString(f.order),"round","1"),"proof.png","image/png",png(),true);
        assertEquals(201,result.statusCode(),result.body()); return data(result).get("id").asString();
    }
    private void pending(Flow f) throws Exception {
        var result=f.tech.post("/api/work-orders/"+f.order+"/submit-result",Map.of("version",1,"round",1,"result","本轮修复完成","attachmentIds",List.of(voucher(f)),"operationId",op()));
        assertEquals(200,result.statusCode(),result.body());
    }
    private List<Integer> race(Callable<Integer> a,Callable<Integer> b) throws Exception {
        var pool=Executors.newFixedThreadPool(2); var gate=new CountDownLatch(1);
        try {
            var first=pool.submit(() -> {gate.await();return a.call();}); var second=pool.submit(() -> {gate.await();return b.call();}); gate.countDown();
            return List.of(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS));
        } finally {pool.shutdownNow();}
    }
    @Test void unicode_equipment_name_search_works_for_all_three_roles_and_keeps_number_search() throws Exception {
        var f=flow(false);
        for(var browser:List.of(f.owner,f.admin,f.tech)) {
            var response=browser.get("/api/work-orders?keyword="+java.net.URLEncoder.encode("演示空调",java.nio.charset.StandardCharsets.UTF_8));
            assertEquals(200,response.statusCode(),response.body()); assertEquals(1,data(response).get("total").asInt());
            assertEquals(Long.toString(f.order),data(response).get("records").get(0).get("id").asString());
            var number=browser.get("/api/work-orders?keyword="+f.order); assertEquals(200,number.statusCode(),number.body()); assertEquals(1,data(number).get("total").asInt());
        }
    }
    @Test void manager_can_search_assigned_people_without_expanding_reporter_keyword_scope() throws Exception {
        var f=flow(false); db.update("UPDATE user_account SET name='筛选维修甲' WHERE id=?",f.worker.id);
        String query="/api/work-orders?keyword="+java.net.URLEncoder.encode("筛选维修甲",java.nio.charset.StandardCharsets.UTF_8);
        var result=f.admin.get(query); assertEquals(200,result.statusCode(),result.body()); assertEquals(1,data(result).get("total").asInt());
        assertEquals(0,data(f.owner.get(query)).get("total").asInt());
        var next=account(CurrentUser.Role.TECHNICIAN,CurrentUser.ReviewStatus.APPROVED);
        assertEquals(200,f.admin.post("/api/work-orders/"+f.order+"/assign",Map.of("version",0,"technicianId",next.id.toString(),"reason","接单前改派","operationId",op())).statusCode());
        assertEquals(1,data(f.admin.get(query)).get("total").asInt());
    }
    @Test void technician_rework_precedes_newer_ordinary_in_progress_order() throws Exception {
        var f=flow(true); pending(f);
        assertEquals(200,f.owner.post("/api/work-orders/"+f.order+"/acceptance",Map.of("version",2,"round",1,"passed",false,"reason","需要返工","operationId",op())).statusCode());
        var created=f.owner.post("/api/repair-requests",createBody(upload(f.owner),locationA,op())); String request=data(created).get("resourceId").asString();
        var assigned=f.admin.post("/api/repair-requests/"+request+"/approve-and-assign",Map.of("version",0,"technicianId",f.worker.id.toString(),"confirmPreviouslyRefused",false,"operationId",op())); String other=data(assigned).get("relatedResourceId").asString();
        assertEquals(200,f.tech.post("/api/work-orders/"+other+"/accept",Map.of("version",0,"operationId",op())).statusCode());
        var list=f.tech.get("/api/work-orders?scope=CURRENT"); assertEquals(200,list.statusCode(),list.body());
        assertEquals(Long.toString(f.order),data(list).get("records").get(0).get("id").asString()); assertTrue(data(list).get("records").get(0).get("isRework").asBoolean());
    }
    @Test void accept_vs_reassign_is_serialized_and_same_person_or_post_accept_changes_are_rejected() throws Exception {
        var f=flow(false); var next=account(CurrentUser.Role.TECHNICIAN,CurrentUser.ReviewStatus.APPROVED);
        assertEquals(409,f.admin.post("/api/work-orders/"+f.order+"/assign",Map.of("version",0,"technicianId",f.worker.id.toString(),"reason","同人无效","operationId",op())).statusCode());
        var codes=race(() -> f.tech.post("/api/work-orders/"+f.order+"/accept",Map.of("version",0,"operationId",op())).statusCode(),
            () -> f.admin.post("/api/work-orders/"+f.order+"/assign",Map.of("version",0,"technicianId",next.id.toString(),"reason","接单前协调","operationId",op())).statusCode());
        assertEquals(1,codes.stream().filter(c -> c==200).count()); assertTrue(codes.contains(403)||codes.contains(409));
        if(codes.getFirst()==200) {
            assertEquals(409,f.admin.post("/api/work-orders/"+f.order+"/assign",Map.of("version",1,"technicianId",next.id.toString(),"reason","接单后无效","operationId",op())).statusCode());
            assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM repair_round WHERE work_order_id=?",Integer.class,f.order));
        } else {
            assertEquals("ASSIGNMENT_ONLY",data(f.tech.get("/api/work-orders/"+f.order)).get("historyScope").asString());
            assertEquals(0,data(f.tech.get("/api/work-orders?scope=CURRENT")).get("total").asInt());
        }
    }
    @Test void concurrent_same_marker_progress_is_one_record_and_receipt_survives_state_change() throws Exception {
        var f=flow(true); String marker=op(); var body=Map.of("version",1,"round",1,"content","独立记录一次","operationId",marker);
        assertEquals(List.of(200,200),race(() -> f.tech.post("/api/work-orders/"+f.order+"/progress",body).statusCode(),() -> f.tech.post("/api/work-orders/"+f.order+"/progress",body).statusCode()));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM repair_progress WHERE work_order_id=?",Integer.class,f.order));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM work_order_event WHERE work_order_id=? AND operation_id=?",Integer.class,f.order,marker));
        var submitted=f.tech.post("/api/work-orders/"+f.order+"/submit-result",Map.of("version",2,"round",1,"result","已修好","attachmentIds",List.of(voucher(f)),"operationId",op())); assertEquals(200,submitted.statusCode(),submitted.body());
        assertEquals(200,f.tech.post("/api/work-orders/"+f.order+"/progress",body).statusCode());
        assertEquals(409,f.tech.post("/api/work-orders/"+f.order+"/progress",Map.of("version",1,"round",1,"content","改变内容","operationId",marker)).statusCode());
        var receipt=data(f.tech.get("/api/operations/"+marker+"?operation=PROGRESS_ADDED")); assertEquals("SUCCEEDED",receipt.get("kind").asString()); assertEquals(2,receipt.get("receipt").get("resultVersion").asInt());
        assertEquals("NOT_CONFIRMED",data(f.owner.get("/api/operations/"+marker+"?operation=PROGRESS_ADDED")).get("kind").asString());
    }
    @Test void attachment_binding_failure_rolls_back_earlier_bind_and_order_round_and_event() throws Exception {
        var a=flow(true); var b=flow(true); String valid=voucher(a), foreign=voucher(b);
        var denied=a.tech.post("/api/work-orders/"+a.order+"/submit-result",Map.of("version",1,"round",1,"result","不应提交","attachmentIds",List.of(valid,foreign),"operationId",op()));
        assertEquals(403,denied.statusCode(),denied.body());
        assertEquals("TEMPORARY",db.queryForObject("SELECT state FROM attachment_file WHERE id=?",String.class,Long.parseLong(valid)));
        assertNull(db.queryForObject("SELECT bound_round_id FROM attachment_file WHERE id=?",Long.class,Long.parseLong(valid)));
        assertEquals("IN_PROGRESS",db.queryForObject("SELECT status FROM work_order WHERE id=?",String.class,a.order));
        assertEquals(1,db.queryForObject("SELECT version FROM work_order WHERE id=?",Integer.class,a.order));
        assertNull(db.queryForObject("SELECT result_text FROM repair_round WHERE work_order_id=?",String.class,a.order));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM work_order_event WHERE work_order_id=? AND event_type='RESULT_SUBMITTED'",Integer.class,a.order));
        assertEquals(403,a.tech.upload(Map.of("operationId",op(),"purpose","REPAIR_VOUCHER","workOrderId",Long.toString(b.order),"round","1"),"proof.png","image/png",png(),true).statusCode());
        assertEquals(409,a.tech.upload(Map.of("operationId",op(),"purpose","REPAIR_VOUCHER","workOrderId",Long.toString(a.order),"round","2"),"proof.png","image/png",png(),true).statusCode());
        time.set(time.instant().plusSeconds(24*3600)); a.tech.login(a.worker);
        assertEquals(409,a.tech.post("/api/work-orders/"+a.order+"/submit-result",Map.of("version",1,"round",1,"result","过期凭证无效","attachmentIds",List.of(valid),"operationId",op())).statusCode());
        assertEquals(400,a.tech.post("/api/work-orders/"+a.order+"/submit-result",Map.of("version",1,"round",1,"result","缺少凭证","attachmentIds",List.of(),"operationId",op())).statusCode());
    }
    @Test void acceptance_branches_have_one_winner_and_other_reporter_or_admin_cannot_accept() throws Exception {
        var f=flow(true); pending(f); var stranger=b04Browser(); stranger.login(account(CurrentUser.Role.REPORTER,null));
        assertEquals(403,stranger.post("/api/work-orders/"+f.order+"/acceptance",Map.of("version",2,"round",1,"passed",true,"operationId",op())).statusCode());
        assertEquals(403,f.admin.post("/api/work-orders/"+f.order+"/acceptance",Map.of("version",2,"round",1,"passed",true,"operationId",op())).statusCode());
        assertEquals(400,f.owner.post("/api/work-orders/"+f.order+"/acceptance",Map.of("version",2,"round",1,"passed",false,"reason"," ","operationId",op())).statusCode());
        var passed=Map.of("version",2,"round",1,"passed",true,"operationId",op()); var failed=Map.of("version",2,"round",1,"passed",false,"reason","仍有故障","operationId",op());
        var codes=race(() -> f.owner.post("/api/work-orders/"+f.order+"/acceptance",passed).statusCode(),() -> f.owner.post("/api/work-orders/"+f.order+"/acceptance",failed).statusCode());
        assertEquals(1,codes.stream().filter(c -> c==200).count()); assertEquals(1,codes.stream().filter(c -> c==409).count());
        var winning=codes.getFirst()==200?passed:failed;
        assertEquals(200,f.owner.post("/api/work-orders/"+f.order+"/acceptance",winning).statusCode());
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM repair_round WHERE work_order_id=? AND acceptance_at IS NOT NULL",Integer.class,f.order));
        assertEquals(codes.getFirst()==200?1:2,db.queryForObject("SELECT COUNT(*) FROM repair_round WHERE work_order_id=?",Integer.class,f.order));
    }
    @Test void passed_acceptance_must_not_silently_ignore_a_supplied_reason() throws Exception {
        var f=flow(true); pending(f);
        var result=f.owner.post("/api/work-orders/"+f.order+"/acceptance",Map.of("version",2,"round",1,"passed",true,"reason","隐藏业务字段","operationId",op()));
        assertEquals(400,result.statusCode(),result.body());
        assertEquals("PENDING_ACCEPTANCE",db.queryForObject("SELECT status FROM work_order WHERE id=?",String.class,f.order));
    }
}
