package cn.wzbc.maintenance.support;

import cn.wzbc.maintenance.Application;
import cn.wzbc.maintenance.account.entity.UserEntity;
import cn.wzbc.maintenance.security.CurrentUser;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import static org.junit.jupiter.api.Assertions.*;

/** Explicitly selected only; real isolated backend, owned fixture IDs, graceful file-controlled stop. */
@SpringBootTest(classes={Application.class,B02HttpTestSupport.TimeConfig.class},webEnvironment=SpringBootTest.WebEnvironment.DEFINED_PORT,properties={"server.port=8081"})
class B10BrowserHarness extends B04HttpTestSupport {
    private final Map<String,UserEntity> actors=new LinkedHashMap<>();
    private B04Browser owner,manager,worker;
    private Path project() { return Path.of(environment.getRequiredProperty("campus.project-root")).toAbsolutePath().normalize(); }
    private void save(String name,Object value) throws Exception { Files.writeString(project().resolve("tests/evidence/"+name),json.writeValueAsString(value)); }
    private void at(String instant) { time.set(Instant.parse(instant)); }
    private UserEntity actor(String alias,CurrentUser.Role role) {
        var user=account(role,role==CurrentUser.Role.TECHNICIAN?CurrentUser.ReviewStatus.APPROVED:null);
        user.loginName="b10_ui_"+alias;user.name="B10合成"+alias;
        db.update("UPDATE user_account SET login_name=?,name=? WHERE id=?",user.loginName,user.name,user.id);actors.put(alias,user);return user;
    }
    private long request(String name) throws Exception {
        var body=createBody(upload(owner),locationA,UUID.randomUUID().toString());body.put("equipmentName",name);
        return Long.parseLong(data(owner.post("/api/repair-requests",body)).get("resourceId").asString());
    }
    private void ok(B04Browser browser,String path,Map<String,?> body) throws Exception { var response=browser.post(path,body);assertEquals(200,response.statusCode(),response.body()); }
    private long rawOrder(String name,Long equipmentId,String status) throws Exception {
        long req=request(name);var fixture=new B04WorkOrderFixture(db,time.instant());
        long eq=equipmentId==null?fixture.equipment(locationA):equipmentId;if(equipmentId==null)equipment.add(eq);
        long id=fixture.order(req,eq,actors.get("tech").id,actors.get("admin").id,status);
        if(equipmentId==null){db.update("UPDATE work_order SET equipment_id=NULL WHERE id=?",id);db.update("UPDATE repair_request SET matched_equipment_id=NULL WHERE id=?",req);}
        return id;
    }
    private void submit(long order,int version,int round) throws Exception {
        var images=new ArrayList<String>();for(int i=0;i<2;i++) {
            var uploaded=worker.upload(Map.of("operationId",UUID.randomUUID().toString(),"purpose","REPAIR_VOUCHER","workOrderId",Long.toString(order),"round",Integer.toString(round)),"proof.png","image/png",png(),true);
            assertEquals(201,uploaded.statusCode(),uploaded.body());images.add(data(uploaded).get("id").asString());
        }
        ok(worker,"/api/work-orders/"+order+"/submit-result",Map.of("version",version,"round",round,"result","B10第"+round+"轮独立结果","attachmentIds",images,"operationId",UUID.randomUUID().toString()));
    }
    @Test void isolated_browser_fixture_lifecycle() throws Exception {
        Path control=null;boolean ready=false,normal=false;
        try {
            for(String table:List.of("user_account","repair_request","work_order","equipment","attachment_file"))assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM "+table,Integer.class),"Refuse unknown test data");
            control=Files.createDirectory(attachmentRoot.resolve("b10-browser-"+UUID.randomUUID()));assertEquals(attachmentRoot,control.toRealPath().getParent());
            at("2026-09-28T00:00:00Z");actor("admin",CurrentUser.Role.ADMIN);actor("reporter",CurrentUser.Role.REPORTER);actor("tech",CurrentUser.Role.TECHNICIAN);actor("former",CurrentUser.Role.TECHNICIAN);
            owner=b04Browser();owner.login(actors.get("reporter"));manager=b04Browser();manager.login(actors.get("admin"));worker=b04Browser();worker.login(actors.get("tech"));
            var fixture=new B04WorkOrderFixture(db,time.instant());long eq1=fixture.equipment(locationA),eq2=fixture.equipment(locationA);equipment.addAll(List.of(eq1,eq2));
            db.update("UPDATE equipment SET name='同名空调' WHERE id IN (?,?)",eq1,eq2);
            long cross=rawOrder("跨月完成设备",eq1,"COMPLETED");
            db.update("UPDATE work_order SET created_at=?,accepted_at=?,completed_at=? WHERE id=?",Instant.parse("2026-09-29T00:00:00Z"),Instant.parse("2026-09-30T00:00:00Z"),Instant.parse("2026-09-30T16:00:00Z"),cross);
            db.update("UPDATE repair_round SET started_at=?,submitted_at=?,acceptance_at=? WHERE work_order_id=?",Instant.parse("2026-09-30T00:00:00Z"),Instant.parse("2026-09-30T01:00:00Z"),Instant.parse("2026-09-30T16:00:00Z"),cross);
            at("2026-10-15T00:00:00Z");owner.login(actors.get("reporter"));manager.login(actors.get("admin"));worker.login(actors.get("tech"));
            long req=request("无台账返工设备");at("2026-10-15T01:00:00Z");
            var assigned=manager.post("/api/repair-requests/"+req+"/approve-and-assign",Map.of("version",0,"technicianId",actors.get("former").id.toString(),"confirmPreviouslyRefused",false,"operationId",UUID.randomUUID().toString()));assertEquals(200,assigned.statusCode(),assigned.body());
            long rework=Long.parseLong(data(assigned).get("relatedResourceId").asString());var former=b04Browser();former.login(actors.get("former"));
            at("2026-10-15T01:30:00Z");ok(former,"/api/work-orders/"+rework+"/refuse",Map.of("version",0,"reason","B10合成拒单等待","operationId",UUID.randomUUID().toString()));
            at("2026-10-15T02:00:00Z");ok(manager,"/api/work-orders/"+rework+"/assign",Map.of("version",1,"technicianId",actors.get("tech").id.toString(),"confirmPreviouslyRefused",false,"reason","B10人工重新分派","operationId",UUID.randomUUID().toString()));
            at("2026-10-15T03:00:00Z");ok(worker,"/api/work-orders/"+rework+"/accept",Map.of("version",2,"operationId",UUID.randomUUID().toString()));
            at("2026-10-15T04:00:00Z");submit(rework,3,1);
            at("2026-10-15T07:00:00Z");ok(owner,"/api/work-orders/"+rework+"/acceptance",Map.of("version",4,"round",1,"passed",false,"reason","B10仍有故障","operationId",UUID.randomUUID().toString()));
            at("2026-10-15T08:00:00Z");worker.login(actors.get("tech"));owner.login(actors.get("reporter"));manager.login(actors.get("admin"));submit(rework,5,2);
            ok(owner,"/api/work-orders/"+rework+"/acceptance",Map.of("version",6,"round",2,"passed",true,"operationId",UUID.randomUUID().toString()));
            long pending=rawOrder("等待接单设备",eq1,"PENDING_ACCEPT"),progress=rawOrder("处理中设备",eq2,"IN_PROGRESS"),waiting=rawOrder("旧文字待验收设备",null,"IN_PROGRESS"),reassign=rawOrder("待重派无台账设备",null,"PENDING_ACCEPT");
            db.update("UPDATE repair_round SET submitted_at=?,result_text='B10当前待验收结果' WHERE work_order_id=?",time.instant(),waiting);db.update("UPDATE work_order SET status='PENDING_ACCEPTANCE',location_mode='LEGACY_TEXT',location_id=NULL,location_name_snapshot=NULL WHERE id=?",waiting);
            db.update("UPDATE repair_request SET location_mode='LEGACY_TEXT',location_id=NULL,location_name_snapshot=NULL WHERE id=(SELECT request_id FROM work_order WHERE id=?)",waiting);
            db.update("UPDATE repair_request_submission SET location_mode='LEGACY_TEXT',location_id=NULL,location_name_snapshot=NULL WHERE request_id=(SELECT request_id FROM work_order WHERE id=?)",waiting);
            ok(worker,"/api/work-orders/"+reassign+"/refuse",Map.of("version",0,"reason","B10合成待重派","operationId",UUID.randomUUID().toString()));
            var dashboard=data(manager.get("/api/statistics/dashboard?month=2026-10"));assertEquals(2,dashboard.get("completedCount").asInt());assertEquals(91800,dashboard.get("averageResponseSeconds").asDouble());assertEquals(5400,dashboard.get("averageRepairSeconds").asDouble());assertEquals(0,dashboard.get("invalidTimeSampleCount").asInt());
            var metadata=new LinkedHashMap<String,Object>();metadata.put("profile","test");metadata.put("database","campus_maintenance_test");metadata.put("backendPort",port);metadata.put("browserSite","http://localhost:5175/admin/statistics?month=2026-10");metadata.put("controlDirectory",control.toString());metadata.put("accounts",actors.entrySet().stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,e->Map.of("id",e.getValue().id.toString(),"loginName",e.getValue().loginName))));
            metadata.put("orders",Map.of("crossMonth",Long.toString(cross),"rework",Long.toString(rework),"pending",Long.toString(pending),"progress",Long.toString(progress),"waiting",Long.toString(waiting),"reassign",Long.toString(reassign)));metadata.put("equipment",List.of(Long.toString(eq1),Long.toString(eq2)));
            metadata.put("ownedIds",Map.of("accounts",List.copyOf(owned),"buildings",List.copyOf(buildings),"floors",List.copyOf(floors),"locations",List.copyOf(locations),"equipment",List.copyOf(equipment)));
            metadata.put("expected",Map.of("completed",2,"averageResponseSeconds",91800,"averageRepairSeconds",5400,"allCurrent",6,"createdInOctober",5,"unlinkedCreatedInOctober",3,"repairMillisRework",7200000));metadata.put("httpDashboard",dashboard);
            save("B10-browser-ready.json",metadata);Files.writeString(control.resolve("ready"),"ready",StandardOpenOption.CREATE_NEW);ready=true;
            long deadline=System.nanoTime()+Duration.ofMinutes(120).toNanos();
            while(!Files.exists(control.resolve("done")) && System.nanoTime()<deadline) {
                if(Files.exists(control.resolve("revoke-admin"))) {db.update("UPDATE login_session SET revoked_at=?,revoke_reason='LOGOUT' WHERE user_id=? AND revoked_at IS NULL",time.instant(),actors.get("admin").id);Files.delete(control.resolve("revoke-admin"));Files.writeString(control.resolve("revoked-admin"),"done");}
                Thread.sleep(250);
            }
            assertTrue(Files.exists(control.resolve("done")),"Fixture timed out; owned cleanup still runs");normal=true;
        } finally {
            try {if(ready)save("B10-browser-final-db.json",Map.of("orders",db.queryForList("SELECT id,status,equipment_id,CAST(accepted_at AS CHAR) accepted_at,CAST(completed_at AS CHAR) completed_at,current_round FROM work_order WHERE reporter_id=? ORDER BY id",actors.get("reporter").id),"rounds",db.queryForList("SELECT work_order_id,round_no,CAST(started_at AS CHAR) started_at,CAST(submitted_at AS CHAR) submitted_at,CAST(acceptance_at AS CHAR) acceptance_at FROM repair_round WHERE technician_id=? ORDER BY work_order_id,round_no",actors.get("tech").id)));}
            finally {
                b04Cleanup();cleanupOwnedOnly();
                if(control!=null && Files.exists(control,LinkOption.NOFOLLOW_LINKS)) {assertEquals(attachmentRoot,control.toRealPath().getParent());for(String name:List.of("ready","done","revoke-admin","revoked-admin")){Path file=control.resolve(name);if(Files.exists(file,LinkOption.NOFOLLOW_LINKS)){assertTrue(Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS));assertEquals(control.toRealPath(),file.toRealPath().getParent());Files.delete(file);}}Files.delete(control);}
                save("B10-browser-lifecycle.json",Map.of("ready",ready,"normalStop",normal,"ownedCleanupCompleted",true,"endedAtUtc",Instant.now().toString()));
            }
        }
    }
}
