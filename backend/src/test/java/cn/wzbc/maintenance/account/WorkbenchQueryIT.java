package cn.wzbc.maintenance.account;
import cn.wzbc.maintenance.security.CurrentUser;
import cn.wzbc.maintenance.support.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class WorkbenchQueryIT extends B04HttpTestSupport {
    private String op(){return UUID.randomUUID().toString();}
    @Test void approved_request_not_counted_twice() throws Exception {
        var r=account(CurrentUser.Role.REPORTER,null);var a=account(CurrentUser.Role.ADMIN,null);var t=account(CurrentUser.Role.TECHNICIAN,CurrentUser.ReviewStatus.APPROVED);
        var owner=b04Browser();owner.login(r);var admin=browser();admin.login(a);var tech=browser();tech.login(t);
        String request=data(owner.post("/api/repair-requests",createBody(upload(owner),locationA,op()))).get("resourceId").asString();
        assertEquals(1,data(owner.get("/api/workbench")).get("counts").get("pendingReview").asInt());
        var receipt=data(admin.post("/api/repair-requests/"+request+"/approve-and-assign",Map.of("version",0,"operationId",op(),"technicianId",t.id.toString(),"confirmPreviouslyRefused",false)));String order=receipt.get("relatedResourceId").asString();
        int pendingRequestCountForApprovedRequest=data(owner.get("/api/workbench")).get("counts").get("pendingReview").asInt();assertEquals(0,pendingRequestCountForApprovedRequest);
        assertEquals(1,data(tech.get("/api/workbench")).get("counts").get("pendingAccept").asInt());
        assertEquals(200,tech.post("/api/work-orders/"+order+"/refuse",Map.of("version",0,"operationId",op(),"reason","测试重新协调")).statusCode());assertEquals(0,data(tech.get("/api/workbench")).get("counts").get("pendingAccept").asInt());assertEquals(1,data(admin.get("/api/workbench")).get("counts").get("reassignPending").asInt());
    }
    @Test void in_progress_and_rework_are_disjoint_and_unqualified_has_no_workbench() throws Exception {
        var r=account(CurrentUser.Role.REPORTER,null);var a=account(CurrentUser.Role.ADMIN,null);var t=account(CurrentUser.Role.TECHNICIAN,CurrentUser.ReviewStatus.APPROVED);var owner=b04Browser();owner.login(r);var tech=browser();tech.login(t);var f=new B04WorkOrderFixture(db,time.instant());
        for(int round:List.of(1,2)) {long request=Long.parseLong(data(owner.post("/api/repair-requests",createBody(upload(owner),locationA,op()))).get("resourceId").asString());long device=f.equipment(locationA);equipment.add(device);long order=f.order(request,device,t.id,a.id,"IN_PROGRESS");if(round==2)db.update("UPDATE work_order SET current_round=2 WHERE id=?",order);}
        var counts=data(tech.get("/api/workbench")).get("counts");assertEquals(1,counts.get("inProgress").asInt());assertEquals(1,counts.get("rework").asInt());
        for(var status:List.of(CurrentUser.ReviewStatus.PENDING,CurrentUser.ReviewStatus.REJECTED)){var blocked=browser();blocked.login(account(CurrentUser.Role.TECHNICIAN,status));assertEquals(403,blocked.get("/api/workbench").statusCode());assertEquals(200,blocked.get("/api/campus/catalog").statusCode());}
    }
}
