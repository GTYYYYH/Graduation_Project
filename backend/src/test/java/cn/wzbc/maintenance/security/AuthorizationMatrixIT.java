package cn.wzbc.maintenance.security;

import cn.wzbc.maintenance.support.B04HttpTestSupport;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AuthorizationMatrixIT extends B04HttpTestSupport {
    @Test void other_user_cannot_read_attachment() throws Exception {
        var reporter=account(CurrentUser.Role.REPORTER,null);var other=account(CurrentUser.Role.REPORTER,null);var admin=account(CurrentUser.Role.ADMIN,null);
        var tech=account(CurrentUser.Role.TECHNICIAN,CurrentUser.ReviewStatus.APPROVED);var pending=account(CurrentUser.Role.TECHNICIAN,CurrentUser.ReviewStatus.PENDING);
        var owner=b04Browser();owner.login(reporter);var stranger=b04Browser();stranger.login(other);var manager=b04Browser();manager.login(admin);var worker=b04Browser();worker.login(tech);var unqualified=b04Browser();unqualified.login(pending);
        String image=upload(owner),operation=UUID.randomUUID().toString();
        var created=owner.post("/api/repair-requests",createBody(image,locationA,operation));assertEquals(201,created.statusCode(),created.body());String request=data(created).get("resourceId").asString();
        var otherUserAttachmentResponse=stranger.content(image);
        assertEquals(403,otherUserAttachmentResponse.statusCode());
        assertEquals("private, no-store",otherUserAttachmentResponse.headers().firstValue("Cache-Control").orElseThrow());
        assertEquals(200,owner.content(image).statusCode());assertEquals(200,manager.content(image).statusCode());assertEquals(403,worker.content(image).statusCode());assertEquals(403,unqualified.content(image).statusCode());
        assertEquals(403,stranger.get("/api/repair-requests/"+request).statusCode());assertEquals(0,data(stranger.get("/api/repair-requests")).get("total").asInt());
        assertEquals("NOT_CONFIRMED",data(stranger.get("/api/operations/"+operation+"?operation=CREATE_REQUEST")).get("kind").asString());
        assertEquals(403,stranger.get("/api/statistics/dashboard?month=2026-10").statusCode());
        assertEquals(200,unqualified.get("/api/campus/catalog").statusCode());assertEquals(403,unqualified.get("/api/work-orders?scope=CURRENT").statusCode());
        var assigned=manager.post("/api/repair-requests/"+request+"/approve-and-assign",Map.of("version",0,"technicianId",tech.id.toString(),"confirmPreviouslyRefused",false,"operationId",UUID.randomUUID().toString()));assertEquals(200,assigned.statusCode(),assigned.body());String order=data(assigned).get("relatedResourceId").asString();
        assertEquals(200,worker.content(image).statusCode());assertEquals(403,stranger.get("/api/work-orders/"+order).statusCode());assertEquals(0,data(stranger.get("/api/work-orders")).get("total").asInt());
        var evidence=new LinkedHashMap<String,Object>();evidence.put("otherAttachment",otherUserAttachmentResponse.statusCode());evidence.put("request",request);evidence.put("order",order);evidence.put("otherRequestTotal",0);evidence.put("otherOrderTotal",0);evidence.put("unqualifiedCatalog",200);evidence.put("unqualifiedTasks",403);
        java.nio.file.Files.createDirectories(java.nio.file.Path.of("../tests/evidence/T061"));java.nio.file.Files.writeString(java.nio.file.Path.of("../tests/evidence/T061/authorization-matrix.json"),json.writeValueAsString(evidence));
    }
}
