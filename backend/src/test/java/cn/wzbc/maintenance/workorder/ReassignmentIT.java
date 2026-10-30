package cn.wzbc.maintenance.workorder;

import cn.wzbc.maintenance.account.entity.UserEntity;
import cn.wzbc.maintenance.security.CurrentUser;
import cn.wzbc.maintenance.support.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReassignmentIT extends B04HttpTestSupport {
    @Test void pre_accept_assignment_list_excludes_current_person_before_count_and_pagination() throws Exception {
        var reporter=account(CurrentUser.Role.REPORTER,null); var admin=account(CurrentUser.Role.ADMIN,null);
        var current=account(CurrentUser.Role.TECHNICIAN,CurrentUser.ReviewStatus.APPROVED); var other=account(CurrentUser.Role.TECHNICIAN,CurrentUser.ReviewStatus.APPROVED);
        var owner=b04Browser(); owner.login(reporter); var manager=b04Browser(); manager.login(admin);
        var created=owner.post("/api/repair-requests",createBody(upload(owner),locationA,UUID.randomUUID().toString()));
        String request=data(created).get("resourceId").asString();
        var first=manager.get("/api/admin/assignment-eligible?context=FIRST_ASSIGN&requestId="+request);
        assertEquals(2,data(first).get("eligibleTotal").asInt());
        var assigned=manager.post("/api/repair-requests/"+request+"/approve-and-assign",Map.of("version",0,"technicianId",current.id.toString(),"confirmPreviouslyRefused",false,"operationId",UUID.randomUUID().toString()));
        String order=data(assigned).get("relatedResourceId").asString();
        var list=manager.get("/api/admin/assignment-eligible?context=CHANGE_BEFORE_ACCEPT&workOrderId="+order+"&page=1&pageSize=1");
        assertEquals(200,list.statusCode(),list.body());
        assertEquals(1,data(list).get("eligibleTotal").asInt());
        assertEquals(1,data(list).get("candidates").get("total").asInt());
        assertEquals(other.id.toString(),data(list).get("candidates").get("records").get(0).get("id").asString());
        var search=manager.get("/api/admin/assignment-eligible?context=CHANGE_BEFORE_ACCEPT&workOrderId="+order+"&keyword="+current.loginName);
        assertEquals(0,data(search).get("candidates").get("total").asInt());
    }
    @Test void refusal_requires_explicit_confirmation_before_reassigning_former_worker() throws Exception {
        UserEntity reporter=account(CurrentUser.Role.REPORTER,null), admin=account(CurrentUser.Role.ADMIN,null);
        UserEntity first=account(CurrentUser.Role.TECHNICIAN,CurrentUser.ReviewStatus.APPROVED),second=account(CurrentUser.Role.TECHNICIAN,CurrentUser.ReviewStatus.APPROVED);
        var owner=b04Browser(); owner.login(reporter);
        long request=Long.parseLong(data(owner.post("/api/repair-requests",createBody(upload(owner),locationA,UUID.randomUUID().toString()))).get("resourceId").asString());
        var manager=b04Browser(); manager.login(admin);
        var approval=manager.post("/api/repair-requests/"+request+"/approve-and-assign",Map.of("version",0,"technicianId",first.id.toString(),"confirmPreviouslyRefused",false,"operationId",UUID.randomUUID().toString()));
        assertEquals(200,approval.statusCode(),approval.body()); long order=Long.parseLong(data(approval).get("relatedResourceId").asString());
        var former=b04Browser(); former.login(first);
        var refused=former.post("/api/work-orders/"+order+"/refuse",Map.of("version",0,"reason","当前无法到场","operationId",UUID.randomUUID().toString()));
        assertEquals(200,refused.statusCode(),refused.body());
        assertEquals("ASSIGNMENT_ONLY",data(former.get("/api/work-orders/"+order)).get("historyScope").asString());
        assertEquals(0,data(former.get("/api/work-orders/"+order)).get("rounds").size());
        var list=manager.get("/api/admin/assignment-eligible?context=REASSIGN&workOrderId="+order);
        assertEquals(200,list.statusCode(),list.body());
        var oldWorker=data(list).get("candidates").get("records");
        assertTrue(oldWorker.toString().contains("当前无法到场"));
        var unconfirmed=manager.post("/api/work-orders/"+order+"/assign",Map.of("version",1,"technicianId",first.id.toString(),"reason","再次协调","confirmPreviouslyRefused",false,"operationId",UUID.randomUUID().toString()));
        assertEquals(409,unconfirmed.statusCode(),unconfirmed.body());
        var assigned=manager.post("/api/work-orders/"+order+"/assign",Map.of("version",1,"technicianId",second.id.toString(),"reason","安排另一人员","confirmPreviouslyRefused",false,"operationId",UUID.randomUUID().toString()));
        assertEquals(200,assigned.statusCode(),assigned.body());
        assertEquals(order,Long.parseLong(data(assigned).get("resourceId").asString()));
        assertEquals(403,former.post("/api/work-orders/"+order+"/accept",Map.of("version",2,"operationId",UUID.randomUUID().toString())).statusCode());
        assertEquals(0,data(former.get("/api/work-orders?scope=CURRENT")).get("total").asInt());
        assertEquals(1,data(former.get("/api/work-orders?scope=HISTORY")).get("total").asInt());
        var next=b04Browser(); next.login(second);
        assertEquals(200,next.post("/api/work-orders/"+order+"/accept",Map.of("version",2,"operationId",UUID.randomUUID().toString())).statusCode());
        assertEquals(0,data(former.get("/api/work-orders/"+order)).get("rounds").size());
    }
    @Test void reassignment_keeps_order_identity() throws Exception {
        UserEntity reporter=account(CurrentUser.Role.REPORTER,null), admin=account(CurrentUser.Role.ADMIN,null);
        UserEntity first=account(CurrentUser.Role.TECHNICIAN,CurrentUser.ReviewStatus.APPROVED),second=account(CurrentUser.Role.TECHNICIAN,CurrentUser.ReviewStatus.APPROVED);
        var creator=b04Browser(); creator.login(reporter);
        long request=Long.parseLong(data(creator.post("/api/repair-requests",createBody(upload(creator),locationA,UUID.randomUUID().toString()))).get("resourceId").asString());
        var fixture=new B04WorkOrderFixture(db,time.instant()); long eq=fixture.equipment(locationA); equipment.add(eq);
        long originalWorkOrderId=fixture.order(request,eq,first.id,admin.id,"PENDING_ACCEPT");
        var manager=b04Browser(); manager.login(admin);
        var response=manager.post("/api/work-orders/"+originalWorkOrderId+"/assign",Map.of("version",0,"technicianId",second.id.toString(),"reason","接单前调整负责人","confirmPreviouslyRefused",false,"operationId",UUID.randomUUID().toString()));
        assertEquals(200,response.statusCode(),response.body());
        long reassignedWorkOrderId=Long.parseLong(data(response).get("resourceId").asString());
        assertEquals(originalWorkOrderId,reassignedWorkOrderId);
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM work_order WHERE request_id=?",Integer.class,request));
    }
}
