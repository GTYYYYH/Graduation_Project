package cn.wzbc.maintenance.account;
import cn.wzbc.maintenance.security.CurrentUser;
import cn.wzbc.maintenance.support.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PersonnelQueryIT extends B04HttpTestSupport {
    @Test void overlapping_categories_do_not_duplicate_total() throws Exception {
        var a=account(CurrentUser.Role.ADMIN,null);var r=account(CurrentUser.Role.REPORTER,null);var t=account(CurrentUser.Role.TECHNICIAN,CurrentUser.ReviewStatus.APPROVED);
        var reporter=b04Browser();reporter.login(r);var admin=browser();admin.login(a);
        var f=new B04WorkOrderFixture(db,time.instant());long device=f.equipment(locationA);equipment.add(device);
        for(String status:List.of("PENDING_ACCEPT","COMPLETED")){
            long request=Long.parseLong(data(reporter.post("/api/repair-requests",createBody(upload(reporter),locationA,UUID.randomUUID().toString()))).get("resourceId").asString());
            db.update("UPDATE repair_request SET status='APPROVED',matched_equipment_id=? WHERE id=?",device,request);
            f.order(request,device,t.id,a.id,status);
        }
        var result=data(admin.get("/api/admin/personnel"));long distinctAccountCount=db.queryForObject("SELECT COUNT(DISTINCT id) FROM user_account",Long.class);long allPeopleTotal=result.get("allPeopleTotal").asLong();assertEquals(distinctAccountCount,allPeopleTotal);
        assertEquals(1,result.get("categoryCounts").get("REPORTER_UNFINISHED").asInt());assertEquals(1,result.get("categoryCounts").get("REPORTER_COMPLETED").asInt());
        var filtered=data(admin.get("/api/admin/personnel?category=REPORTER_COMPLETED&keyword=nomatch"));assertEquals(0,filtered.get("listTotal").asInt());assertEquals(result.get("categoryCounts"),filtered.get("categoryCounts"));
        var history=data(admin.get("/api/admin/personnel/"+r.id+"/history?category=REQUESTS"));assertEquals(2,history.get("records").get("total").asInt());assertEquals(2,history.get("categoryTotals").get("RELATED_ORDERS").asInt());
        assertEquals(403,reporter.get("/api/admin/personnel").statusCode());
    }
}
