package cn.wzbc.maintenance.workorder;

import cn.wzbc.maintenance.account.entity.UserEntity;
import cn.wzbc.maintenance.security.CurrentUser;
import cn.wzbc.maintenance.support.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AcceptanceIT extends B04HttpTestSupport {
    @Test void old_round_cannot_accept_new_round() throws Exception {
        UserEntity reporter=account(CurrentUser.Role.REPORTER,null),admin=account(CurrentUser.Role.ADMIN,null),worker=account(CurrentUser.Role.TECHNICIAN,CurrentUser.ReviewStatus.APPROVED);
        var creator=b04Browser(); creator.login(reporter);
        long request=Long.parseLong(data(creator.post("/api/repair-requests",createBody(upload(creator),locationA,UUID.randomUUID().toString()))).get("resourceId").asString());
        var fixture=new B04WorkOrderFixture(db,time.instant()); long eq=fixture.equipment(locationA); equipment.add(eq);
        long order=fixture.order(request,eq,worker.id,admin.id,"IN_PROGRESS");
        db.update("UPDATE repair_round SET submitted_at=?, result_text='第一轮结果',acceptance_passed=0,acceptance_reason='未解决',accepted_by=?,acceptance_at=? WHERE work_order_id=? AND round_no=1",time.instant(),reporter.id,time.instant(),order);
        db.update("UPDATE work_order SET status='PENDING_ACCEPTANCE',current_round=2,version=3 WHERE id=?",order);
        var browser=b04Browser(); browser.login(reporter);
        var oldRoundResponse=browser.post("/api/work-orders/"+order+"/acceptance",Map.of("version",3,"round",1,"passed",true,"operationId",UUID.randomUUID().toString()));
        assertEquals(409,oldRoundResponse.statusCode());
    }
}
