package cn.wzbc.maintenance.workorder;

import cn.wzbc.maintenance.account.entity.UserEntity;
import cn.wzbc.maintenance.security.CurrentUser;
import cn.wzbc.maintenance.support.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RepairProcessingIT extends B04HttpTestSupport {
    @Test void progress_retry_is_not_duplicated() throws Exception {
        UserEntity reporter=account(CurrentUser.Role.REPORTER,null),admin=account(CurrentUser.Role.ADMIN,null),worker=account(CurrentUser.Role.TECHNICIAN,CurrentUser.ReviewStatus.APPROVED);
        var creator=b04Browser(); creator.login(reporter);
        long request=Long.parseLong(data(creator.post("/api/repair-requests",createBody(upload(creator),locationA,UUID.randomUUID().toString()))).get("resourceId").asString());
        var fixture=new B04WorkOrderFixture(db,time.instant()); long eq=fixture.equipment(locationA); equipment.add(eq);
        long order=fixture.order(request,eq,worker.id,admin.id,"IN_PROGRESS");
        var technician=b04Browser(); technician.login(worker);
        String operation=UUID.randomUUID().toString(); var body=Map.of("version",0,"round",1,"content","已检查电源","operationId",operation);
        var first=technician.post("/api/work-orders/"+order+"/progress",body); assertEquals(200,first.statusCode(),first.body());
        var retry=technician.post("/api/work-orders/"+order+"/progress",body); assertEquals(200,retry.statusCode(),retry.body());
        int progressCountAfterRetry=db.queryForObject("SELECT COUNT(*) FROM repair_progress WHERE work_order_id=?",Integer.class,order);
        assertEquals(1,progressCountAfterRetry);
    }
}
