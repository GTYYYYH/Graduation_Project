package cn.wzbc.maintenance.workorder;

import cn.wzbc.maintenance.account.entity.UserEntity;
import cn.wzbc.maintenance.security.CurrentUser;
import cn.wzbc.maintenance.support.B04HttpTestSupport;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Real per-session counts across actual workflow transitions, not unread flags. */
class WorkOrderNavigationCountIT extends B04HttpTestSupport {
    private static final String COUNT="/api/work-orders/navigation-count";
    private String op() { return UUID.randomUUID().toString(); }
    private record Flow(long order,UserEntity worker,B04Browser reporter,B04Browser manager,B04Browser tech) {}
    private Flow flow() throws Exception {
        var reporter=account(CurrentUser.Role.REPORTER,null); var manager=account(CurrentUser.Role.ADMIN,null);
        var worker=account(CurrentUser.Role.TECHNICIAN,CurrentUser.ReviewStatus.APPROVED);
        var owner=b04Browser(); owner.login(reporter); var admin=b04Browser(); admin.login(manager); var tech=b04Browser(); tech.login(worker);
        var submitted=owner.post("/api/repair-requests",createBody(upload(owner),locationA,op())); assertEquals(201,submitted.statusCode());
        String request=data(submitted).get("resourceId").asString();
        var approved=admin.post("/api/repair-requests/"+request+"/approve-and-assign",Map.of("version",0,"technicianId",worker.id.toString(),"confirmPreviouslyRefused",false,"operationId",op()));
        assertEquals(200,approved.statusCode()); return new Flow(Long.parseLong(data(approved).get("relatedResourceId").asString()),worker,owner,admin,tech);
    }
    private int count(B04Browser browser) throws Exception {
        var result=browser.get(COUNT); assertEquals(200,result.statusCode(),result.body()); return data(result).get("count").asInt();
    }
    private void counts(Flow f,int tech,int reporter) throws Exception { assertEquals(tech,count(f.tech)); assertEquals(reporter,count(f.reporter)); assertEquals(tech+reporter==0?0:1,count(f.manager)); }
    private void result(Flow f,int version,int round) throws Exception {
        var image=f.tech.upload(Map.of("operationId",op(),"purpose","REPAIR_VOUCHER","workOrderId",Long.toString(f.order),"round",Integer.toString(round)),"proof.png","image/png",png(),true);
        assertEquals(201,image.statusCode());
        assertEquals(200,f.tech.post("/api/work-orders/"+f.order+"/submit-result",Map.of("version",version,"round",round,"result","本轮处理完成","attachmentIds",List.of(data(image).get("id").asString()),"operationId",op())).statusCode());
    }
    @Test void counts_follow_pending_accept_in_progress_rework_pending_acceptance_and_completion() throws Exception {
        var f=flow(); counts(f,1,0);
        var identity=data(f.tech.get(COUNT)); assertEquals(f.worker.id.toString(),identity.get("userId").asString()); assertEquals("TECHNICIAN",identity.get("role").asString());
        assertEquals(200,f.tech.post("/api/work-orders/"+f.order+"/accept",Map.of("version",0,"operationId",op())).statusCode()); counts(f,1,0);
        assertEquals(200,f.tech.post("/api/work-orders/"+f.order+"/progress",Map.of("version",1,"round",1,"content","独立进度不重复计数","operationId",op())).statusCode()); counts(f,1,0);
        result(f,2,1); counts(f,0,1);
        assertEquals(200,f.reporter.post("/api/work-orders/"+f.order+"/acceptance",Map.of("version",3,"round",1,"passed",false,"reason","仍需返工","operationId",op())).statusCode()); counts(f,1,0);
        result(f,4,2); counts(f,0,1);
        var passed=new LinkedHashMap<String,Object>(Map.of("version",5,"round",2,"passed",true,"operationId",op())); passed.put("reason",null);
        assertEquals(200,f.reporter.post("/api/work-orders/"+f.order+"/acceptance",passed).statusCode()); counts(f,0,0);
    }
    @Test void refused_and_replaced_assignments_do_not_count_for_the_former_worker_or_other_people() throws Exception {
        var f=flow(); var otherReporter=account(CurrentUser.Role.REPORTER,null); var otherWorker=account(CurrentUser.Role.TECHNICIAN,CurrentUser.ReviewStatus.APPROVED);
        var other=b04Browser(); other.login(otherReporter); var second=b04Browser(); second.login(otherWorker);
        assertEquals(0,count(other)); assertEquals(0,count(second));
        assertEquals(200,f.tech.post("/api/work-orders/"+f.order+"/refuse",Map.of("version",0,"reason","无法到场","operationId",op())).statusCode()); assertEquals(0,count(f.tech)); assertEquals(0,count(f.reporter)); assertEquals(1,count(f.manager));
        assertEquals(200,f.manager.post("/api/work-orders/"+f.order+"/assign",Map.of("version",1,"technicianId",otherWorker.id.toString(),"reason","安排另一人","confirmPreviouslyRefused",false,"operationId",op())).statusCode());
        assertEquals(0,count(f.tech)); assertEquals(1,count(second)); assertEquals(0,count(other));
        assertEquals(200,f.manager.post("/api/work-orders/"+f.order+"/assign",Map.of("version",2,"technicianId",f.worker.id.toString(),"reason","协调原人","confirmPreviouslyRefused",true,"operationId",op())).statusCode());
        assertEquals(1,count(f.tech)); assertEquals(0,count(second));
    }
    @Test void administrator_counts_all_unfinished_orders_across_reporters_and_workers() throws Exception {
        var first=flow(); var second=flow();
        assertEquals(2,count(first.manager)); assertEquals(2,count(second.manager));
        assertEquals(200,first.tech.post("/api/work-orders/"+first.order+"/refuse",Map.of("version",0,"reason","待重新安排","operationId",op())).statusCode());
        assertEquals(2,count(first.manager));
        assertEquals(200,second.tech.post("/api/work-orders/"+second.order+"/accept",Map.of("version",0,"operationId",op())).statusCode());
        result(second,1,1); assertEquals(2,count(first.manager));
        var passed=new LinkedHashMap<String,Object>(Map.of("version",2,"round",1,"passed",true,"operationId",op())); passed.put("reason",null);
        assertEquals(200,second.reporter.post("/api/work-orders/"+second.order+"/acceptance",passed).statusCode());
        assertEquals(1,count(first.manager)); assertEquals(1,count(second.manager));
        var identity=data(first.manager.get(COUNT)); assertEquals("ADMIN",identity.get("role").asString());
        assertEquals(400,first.manager.get(COUNT+"?status=COMPLETED").statusCode());
    }
    @Test void count_requires_qualified_current_identity_and_rejects_scope_overrides() throws Exception {
        var f=flow(); assertEquals(401,b04Browser().get(COUNT).statusCode()); assertEquals(1,count(f.manager));
        var pending=account(CurrentUser.Role.TECHNICIAN,CurrentUser.ReviewStatus.PENDING); var limited=b04Browser(); limited.login(pending);
        assertEquals(403,limited.get(COUNT).statusCode());
        assertEquals(400,f.tech.get(COUNT+"?userId="+f.worker.id).statusCode());
        db.update("UPDATE user_account SET enabled=0 WHERE id=?",f.worker.id); assertEquals(401,f.tech.get(COUNT).statusCode());
    }
}
