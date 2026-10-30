package cn.wzbc.maintenance.workorder;

import cn.wzbc.maintenance.account.entity.UserEntity;
import cn.wzbc.maintenance.security.CurrentUser;
import cn.wzbc.maintenance.support.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AcceptRefuseIT extends B04HttpTestSupport {
    @Test void concurrent_accept_and_refuse_have_one_winner() throws Exception {
        UserEntity reporter=account(CurrentUser.Role.REPORTER,null), admin=account(CurrentUser.Role.ADMIN,null), worker=account(CurrentUser.Role.TECHNICIAN,CurrentUser.ReviewStatus.APPROVED);
        var owner=b04Browser(); owner.login(reporter);
        long request=Long.parseLong(data(owner.post("/api/repair-requests",createBody(upload(owner),locationA,UUID.randomUUID().toString()))).get("resourceId").asString());
        var fixture=new B04WorkOrderFixture(db,time.instant()); long eq=fixture.equipment(locationA); equipment.add(eq);
        long order=fixture.order(request,eq,worker.id,admin.id,"PENDING_ACCEPT");
        var technician=b04Browser(); technician.login(worker);
        var gate=new CountDownLatch(1); var pool=Executors.newFixedThreadPool(2);
        try {
            var accept=pool.submit(() -> { gate.await(); return technician.post("/api/work-orders/"+order+"/accept",Map.of("version",0,"operationId",UUID.randomUUID().toString())); });
            var refuse=pool.submit(() -> { gate.await(); return technician.post("/api/work-orders/"+order+"/refuse",Map.of("version",0,"reason","无法处理","operationId",UUID.randomUUID().toString())); });
            gate.countDown(); var accepted=accept.get(15,TimeUnit.SECONDS);var refused=refuse.get(15,TimeUnit.SECONDS);
            var codes=List.of(accepted.statusCode(),refused.statusCode());
            assertEquals(1,codes.stream().filter(code -> code==200).count(),"Actual HTTP status codes: "+codes);
            String status=db.queryForObject("SELECT status FROM work_order WHERE id=?",String.class,order);
            assertTrue(Set.of("IN_PROGRESS","REASSIGN_PENDING").contains(status));
            if(status.equals("REASSIGN_PENDING")) {assertEquals(200,refused.statusCode(),refused.body());assertEquals(403,accepted.statusCode(),accepted.body());assertEquals("RECORD_FORBIDDEN",code(accepted));}
            else {assertEquals(200,accepted.statusCode(),accepted.body());assertEquals(409,refused.statusCode(),refused.body());assertEquals("VERSION_CONFLICT",code(refused));}
            assertEquals(status.equals("IN_PROGRESS")?1:0,db.queryForObject("SELECT COUNT(*) FROM repair_round WHERE work_order_id=?",Integer.class,order));
            assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM work_order_event WHERE work_order_id=? AND event_type IN ('ACCEPTED','REFUSED')",Integer.class,order));
        } finally { pool.shutdownNow(); }
    }
    @Test void refusal_first_deterministically_explains_the_concurrent_loser() throws Exception {
        UserEntity reporter=account(CurrentUser.Role.REPORTER,null),admin=account(CurrentUser.Role.ADMIN,null),worker=account(CurrentUser.Role.TECHNICIAN,CurrentUser.ReviewStatus.APPROVED);
        var owner=b04Browser();owner.login(reporter);
        long request=Long.parseLong(data(owner.post("/api/repair-requests",createBody(upload(owner),locationA,UUID.randomUUID().toString()))).get("resourceId").asString());
        var fixture=new B04WorkOrderFixture(db,time.instant());long eq=fixture.equipment(locationA);equipment.add(eq);long order=fixture.order(request,eq,worker.id,admin.id,"PENDING_ACCEPT");
        var tech=b04Browser();tech.login(worker);
        var refusal=tech.post("/api/work-orders/"+order+"/refuse",Map.of("version",0,"reason","按确定顺序先完成拒单","operationId",UUID.randomUUID().toString()));assertEquals(200,refusal.statusCode(),refusal.body());
        var lateAccept=tech.post("/api/work-orders/"+order+"/accept",Map.of("version",0,"operationId",UUID.randomUUID().toString()));
        java.nio.file.Files.writeString(java.nio.file.Path.of("../tests/evidence/B08-concurrency-diagnostic.json"),json.writeValueAsString(Map.of("interleaving","refuse commits before old accept","refusalHttp",refusal.statusCode(),"lateAcceptHttp",lateAccept.statusCode(),"lateAcceptBody",json.readTree(lateAccept.body()),"order",db.queryForMap("SELECT status,version,current_round,assigned_technician_id FROM work_order WHERE id=?",order),"roundCount",db.queryForObject("SELECT COUNT(*) FROM repair_round WHERE work_order_id=?",Integer.class,order))));
        assertEquals(403,lateAccept.statusCode(),lateAccept.body());assertEquals("RECORD_FORBIDDEN",code(lateAccept));
        assertEquals("REASSIGN_PENDING",db.queryForObject("SELECT status FROM work_order WHERE id=?",String.class,order));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM repair_round WHERE work_order_id=?",Integer.class,order));
        long secondRequest=Long.parseLong(data(owner.post("/api/repair-requests",createBody(upload(owner),locationA,UUID.randomUUID().toString()))).get("resourceId").asString());
        long secondEq=fixture.equipment(locationA);equipment.add(secondEq);long second=fixture.order(secondRequest,secondEq,worker.id,admin.id,"PENDING_ACCEPT");
        var firstAccept=tech.post("/api/work-orders/"+second+"/accept",Map.of("version",0,"operationId",UUID.randomUUID().toString()));assertEquals(200,firstAccept.statusCode(),firstAccept.body());
        var lateRefuse=tech.post("/api/work-orders/"+second+"/refuse",Map.of("version",0,"reason","旧拒单","operationId",UUID.randomUUID().toString()));assertEquals(409,lateRefuse.statusCode(),lateRefuse.body());assertEquals("VERSION_CONFLICT",code(lateRefuse));
        assertEquals("IN_PROGRESS",db.queryForObject("SELECT status FROM work_order WHERE id=?",String.class,second));assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM repair_round WHERE work_order_id=?",Integer.class,second));
        java.nio.file.Files.writeString(java.nio.file.Path.of("../tests/evidence/B08-concurrency-resolution.json"),json.writeValueAsString(Map.of("refusalFirst",Map.of("winner",refusal.statusCode(),"loser",lateAccept.statusCode(),"loserCode",code(lateAccept),"rounds",0),"acceptFirst",Map.of("winner",firstAccept.statusCode(),"loser",lateRefuse.statusCode(),"loserCode",code(lateRefuse),"rounds",1),"productionCodeChanged",false)));
    }
    @Test void wrong_technician_cannot_accept() throws Exception {
        UserEntity reporter=account(CurrentUser.Role.REPORTER,null), admin=account(CurrentUser.Role.ADMIN,null);
        UserEntity assigned=account(CurrentUser.Role.TECHNICIAN,CurrentUser.ReviewStatus.APPROVED);
        UserEntity other=account(CurrentUser.Role.TECHNICIAN,CurrentUser.ReviewStatus.APPROVED);
        var creator=b04Browser(); creator.login(reporter);
        var created=creator.post("/api/repair-requests",createBody(upload(creator),locationA,UUID.randomUUID().toString()));
        long request=Long.parseLong(data(created).get("resourceId").asString());
        var fixture=new B04WorkOrderFixture(db,time.instant()); long equipmentId=fixture.equipment(locationA); equipment.add(equipmentId);
        long order=fixture.order(request,equipmentId,assigned.id,admin.id,"PENDING_ACCEPT");
        var wrong=b04Browser(); wrong.login(other);
        var otherTechnicianAcceptResponse=wrong.post("/api/work-orders/"+order+"/accept",Map.of("version",0,"operationId",UUID.randomUUID().toString()));
        assertEquals(403,otherTechnicianAcceptResponse.statusCode());
        assertEquals("PENDING_ACCEPT",db.queryForObject("SELECT status FROM work_order WHERE id=?",String.class,order));
    }
}
