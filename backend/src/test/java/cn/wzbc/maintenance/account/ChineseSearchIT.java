package cn.wzbc.maintenance.account;

import cn.wzbc.maintenance.security.CurrentUser;
import cn.wzbc.maintenance.support.B04HttpTestSupport;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChineseSearchIT extends B04HttpTestSupport {
    @Test void chinese_keywords_work_with_ascii_business_numbers() throws Exception {
        var admin=account(CurrentUser.Role.ADMIN,null);
        var reporter=account(CurrentUser.Role.REPORTER,null);
        var manager=b04Browser(); manager.login(admin);
        var submitter=b04Browser(); submitter.login(reporter);
        var request=submitter.post("/api/repair-requests",createBody(upload(submitter),locationA,UUID.randomUUID().toString()));
        assertEquals(201,request.statusCode());
        var equipment=manager.post("/api/equipment",Map.of("operationId",UUID.randomUUID().toString(),"name","演示空调","locationId",Long.toString(locationA),"addressText","台账原文","facilityType","AIR_CONDITIONER"));
        assertEquals(201,equipment.statusCode());
        String keyword=URLEncoder.encode("演示空调",StandardCharsets.UTF_8);
        var responses=List.of(manager.get("/api/repair-requests?keyword="+keyword),submitter.get("/api/repair-requests?keyword="+keyword),manager.get("/api/equipment?keyword="+keyword),manager.get("/api/admin/personnel/"+reporter.id+"/history?category=REQUESTS&keyword="+keyword));
        assertAll(responses.stream().map(response -> (org.junit.jupiter.api.function.Executable)()->assertEquals(200,response.statusCode(),response.body())));
        for(int i=0;i<3;i++) assertEquals(1,data(responses.get(i)).get("total").asInt());
        assertEquals(1,data(responses.get(3)).get("records").get("total").asInt());
    }
}
