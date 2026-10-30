package cn.wzbc.maintenance.statistics;

import cn.wzbc.maintenance.account.entity.UserEntity;
import cn.wzbc.maintenance.security.CurrentUser;
import cn.wzbc.maintenance.support.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import cn.wzbc.maintenance.statistics.service.StatisticsService;
import tools.jackson.databind.JsonNode;
import static org.junit.jupiter.api.Assertions.*;

/** Real isolated MySQL/HTTP. Expectations are hand-calculated, never derived from statistics code. */
class StatisticsIT extends B04HttpTestSupport {
    @Autowired StatisticsService statistics;
    private UserEntity reporter, admin, technician;
    private B04Browser manager, owner, worker;

    private void setup() throws Exception {
        reporter=account(CurrentUser.Role.REPORTER,null);
        admin=account(CurrentUser.Role.ADMIN,null);
        technician=account(CurrentUser.Role.TECHNICIAN,CurrentUser.ReviewStatus.APPROVED);
        manager=b04Browser(); manager.login(admin);
        owner=b04Browser(); owner.login(reporter);
        worker=b04Browser(); worker.login(technician);
    }
    private long order(String status, boolean unlinked) throws Exception {
        var request=owner.post("/api/repair-requests",createBody(upload(owner),locationA,UUID.randomUUID().toString()));
        long requestId=Long.parseLong(data(request).get("resourceId").asString());
        var fixture=new B04WorkOrderFixture(db,time.instant());
        long eq=fixture.equipment(locationA); equipment.add(eq);
        long id=fixture.order(requestId,eq,technician.id,admin.id,status);
        if(unlinked) {
            db.update("UPDATE work_order SET equipment_id=NULL WHERE id=?",id);
            db.update("UPDATE repair_request SET matched_equipment_id=NULL WHERE id=?",requestId);
        }
        return id;
    }
    private String voucher(long id,int round) throws Exception {
        var r=worker.upload(Map.of("operationId",UUID.randomUUID().toString(),"purpose","REPAIR_VOUCHER","workOrderId",Long.toString(id),"round",Integer.toString(round)),"proof.png","image/png",png(),true);
        assertEquals(201,r.statusCode(),r.body()); return data(r).get("id").asString();
    }
    private void result(long id,int version,int round) throws Exception {
        var r=worker.post("/api/work-orders/"+id+"/submit-result",Map.of("version",version,"round",round,"result","本轮独立维修结果","attachmentIds",List.of(voucher(id,round),voucher(id,round)),"operationId",UUID.randomUUID().toString()));
        assertEquals(200,r.statusCode(),r.body());
    }
    private void accept(long id,int version,int round,boolean passed) throws Exception {
        var body=new HashMap<String,Object>(Map.of("version",version,"round",round,"passed",passed,"operationId",UUID.randomUUID().toString()));
        if(!passed)body.put("reason","仍有故障，需要返工");
        var r=owner.post("/api/work-orders/"+id+"/acceptance",body); assertEquals(200,r.statusCode(),r.body());
    }
    private JsonNode dashboard(String month) throws Exception {
        var r=manager.get("/api/statistics/dashboard?month="+month); assertEquals(200,r.statusCode(),r.body()); return data(r);
    }
    private JsonNode details(String query) throws Exception {
        var r=manager.get("/api/statistics/details?"+query); assertEquals(200,r.statusCode(),r.body()); return data(r);
    }
    private void times(long id,String first,String created,String accepted,String completed,long repairMillis) {
        var firstTime=Instant.parse(first);var createdTime=Instant.parse(created);
        Instant acceptedTime=accepted==null?null:Instant.parse(accepted),completedTime=completed==null?null:Instant.parse(completed);
        db.update("UPDATE repair_request SET created_at=?,first_submitted_at=? WHERE id=(SELECT request_id FROM work_order WHERE id=?)",firstTime,firstTime,id);
        db.update("UPDATE work_order SET created_at=?,accepted_at=?,completed_at=? WHERE id=?",createdTime,acceptedTime,completedTime,id);
        if(acceptedTime!=null)db.update("UPDATE repair_round SET created_at=?,started_at=?,submitted_at=?,acceptance_at=? WHERE work_order_id=?",acceptedTime,acceptedTime,acceptedTime.plusMillis(repairMillis),completedTime,id);
    }
    @Test void repair_duration_excludes_acceptance_wait() throws Exception {
        setup(); long id=order("IN_PROGRESS",true);
        var former=account(CurrentUser.Role.TECHNICIAN,CurrentUser.ReviewStatus.APPROVED);
        long submission=db.queryForObject("SELECT current_submission_id FROM repair_request WHERE id=(SELECT request_id FROM work_order WHERE id=?)",Long.class,id);
        new B04WorkOrderFixture(db,time.instant().minusSeconds(7200)).formerBeforeCurrent(id,former.id,admin.id,submission);
        db.update("UPDATE work_order SET created_at=?,accepted_at=? WHERE id=?",time.instant().minusSeconds(10000),time.instant(),id);
        db.update("UPDATE repair_round SET started_at=? WHERE work_order_id=?",time.instant(),id);
        db.update("UPDATE repair_request SET first_submitted_at=? WHERE id=(SELECT request_id FROM work_order WHERE id=?)",time.instant().minusSeconds(10800),id);
        time.set(time.instant().plusSeconds(3600)); result(id,0,1);
        time.set(time.instant().plusSeconds(10800)); accept(id,1,1,false);
        time.set(time.instant().plusSeconds(3600)); result(id,2,2); accept(id,3,2,true);
        var dashboard=dashboard("2026-10");
        assertEquals(1,dashboard.get("completedCount").asInt());
        assertEquals(7200,dashboard.get("averageRepairSeconds").asDouble());
        assertEquals(10800,dashboard.get("averageResponseSeconds").asDouble());
        assertEquals("2026-10-11T21:00:00.000Z",data(manager.get("/api/work-orders/"+id)).get("firstSubmittedAt").asString());
        var row=details("metric=REPAIR_DURATION&month=2026-10").get("records").get("records").get(0);
        assertEquals(Long.toString(id),row.get("id").asString());
        long cumulativeRepairMillis=Math.round(row.get("repairSeconds").asDouble()*1000);
        assertEquals(7200000L, cumulativeRepairMillis);
        assertEquals(4,db.queryForObject("SELECT COUNT(*) FROM attachment_file WHERE bound_round_id IN(SELECT id FROM repair_round WHERE work_order_id=?)",Integer.class,id));
        assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM work_order_assignment WHERE work_order_id=?",Integer.class,id));
        java.nio.file.Files.writeString(java.nio.file.Path.of("../tests/evidence/B10-repair-hand-calculation.json"),json.writeValueAsString(Map.of("expectedCumulativeRepairMillis",7200000L,"actualCumulativeRepairMillis",cumulativeRepairMillis,"dashboard",dashboard,"detail",row)));
    }
    @Test void empty_samples_are_null_and_admin_only() throws Exception {
        setup(); var d=dashboard("2026-10");
        assertEquals(0,d.get("completedCount").asInt()); assertEquals(0,d.get("sampleCount").asInt());
        assertTrue(d.get("averageResponseSeconds").isNull()); assertTrue(d.get("averageRepairSeconds").isNull());
        assertEquals(31,d.get("dailyCompleted").size()); assertEquals(0,d.get("equipmentFrequencies").size());
        for(var b:List.of(owner,worker)) {
            assertEquals(403,b.get("/api/statistics/dashboard?month=2026-10").statusCode());
            assertEquals(403,b.get("/api/statistics/details?metric=CURRENT_STATE").statusCode());
        }
        assertEquals(401,browser().get("/api/statistics/dashboard?month=2026-10").statusCode());
    }
    @Test void shanghai_month_day_boundaries_cross_month_and_equipment_identity_reconcile() throws Exception {
        setup();long a=order("COMPLETED",false),b=order("COMPLETED",false),c=order("COMPLETED",false),d=order("COMPLETED",false);
        long e=order("PENDING_ACCEPT",true),f=order("PENDING_ACCEPT",true),g=order("PENDING_ACCEPT",false),h=order("PENDING_ACCEPT",false);
        times(a,"2026-09-29T00:00:00Z","2026-09-29T01:00:00Z","2026-09-30T12:00:00Z","2026-09-30T16:00:00Z",1001);
        times(b,"2026-09-29T00:00:00Z","2026-09-30T16:00:00Z","2026-09-30T16:00:00Z","2026-10-31T15:59:59.999Z",2002);
        times(c,"2026-10-30T00:00:00Z","2026-10-31T15:59:58Z","2026-10-31T15:59:59Z","2026-10-31T16:00:00Z",500);
        times(d,"2026-09-28T00:00:00Z","2026-09-28T01:00:00Z","2026-09-29T00:00:00Z","2026-09-30T15:59:59.999Z",1000);
        for(long id:List.of(e,f,g))times(id,"2026-10-11T00:00:00Z","2026-10-12T00:00:00Z",null,null,0);
        times(h,"2026-10-30T00:00:00Z","2026-10-31T16:00:00Z",null,null,0);
        db.update("UPDATE work_order SET location_mode='LEGACY_TEXT',location_id=NULL,location_name_snapshot=NULL WHERE id=?",e);
        var october=dashboard("2026-10");
        assertEquals(2,october.get("completedCount").asInt());assertEquals(2,october.get("sampleCount").asInt());
        assertEquals(136800,october.get("averageResponseSeconds").asDouble());
        assertEquals(1.502,october.get("averageRepairSeconds").asDouble());
        int dailyTotal=0;for(var day:october.get("dailyCompleted"))dailyTotal+=day.get("count").asInt();assertEquals(2,dailyTotal);
        assertEquals(1,october.get("dailyCompleted").get(0).get("count").asInt());assertEquals(1,october.get("dailyCompleted").get(30).get("count").asInt());
        assertEquals(4,october.get("equipmentFrequencies").size());
        assertTrue(october.get("equipmentFrequencies").get(0).get("equipment").isNull());assertEquals(2,october.get("equipmentFrequencies").get(0).get("count").asInt());
        var distinct=new HashSet<String>();for(var frequency:october.get("equipmentFrequencies"))if(!frequency.get("equipment").isNull()) {distinct.add(frequency.get("equipment").get("id").asString());assertEquals("演示空调",frequency.get("equipment").get("name").asString());assertEquals(1,frequency.get("count").asInt());}
        assertEquals(3,distinct.size());
        assertEquals(october.get("stateCounts"),dashboard("2026-09").get("stateCounts"));assertEquals(october.get("stateCounts"),dashboard("2026-11").get("stateCounts"));
        assertEquals(4,october.get("stateCounts").get("COMPLETED").asInt());assertEquals(4,october.get("stateCounts").get("PENDING_ACCEPT").asInt());
        for(String metric:List.of("COMPLETED","RESPONSE_DURATION","REPAIR_DURATION"))assertEquals(2,details("metric="+metric+"&month=2026-10").get("records").get("total").asInt());
        assertEquals(Long.toString(a),details("metric=DAILY_COMPLETED&month=2026-10&date=2026-10-01").get("records").get("records").get(0).get("id").asString());
        assertEquals(Long.toString(b),details("metric=DAILY_COMPLETED&month=2026-10&date=2026-10-31").get("records").get("records").get(0).get("id").asString());
        for(String eq:distinct)assertEquals(1,details("metric=EQUIPMENT_FREQUENCY&month=2026-10&equipmentId="+eq).get("records").get("total").asInt());
        var unlinked=details("metric=EQUIPMENT_FREQUENCY&month=2026-10&unlinkedEquipment=true");assertEquals("MONTH_CREATED",unlinked.get("scope").asString());assertEquals(2,unlinked.get("records").get("total").asInt());
        for(var row:unlinked.get("records").get("records")){assertTrue(row.get("equipment").isNull());assertEquals("演示空调",row.get("reportedEquipmentName").asString());assertFalse(row.get("hasInvalidTime").asBoolean());}
        var all=details("metric=CURRENT_STATE");assertEquals(8,all.get("records").get("total").asInt());assertTrue(all.get("month").isNull());assertEquals("ALL_CURRENT_STATE",all.get("scope").asString());
        java.nio.file.Files.writeString(java.nio.file.Path.of("../tests/evidence/B10-boundary-hand-calculation.json"),json.writeValueAsString(Map.of("expectedCompleted",2,"expectedResponseSeconds",136800,"expectedRepairSeconds",1.502,"expectedCreated",5,"expectedAllCurrent",8,"actual",october,"unlinkedDetails",unlinked)));
    }
    @Test void missing_and_negative_times_report_quality_without_dropping_samples() throws Exception {
        setup();long missing=order("COMPLETED",false);
        db.update("DELETE FROM repair_round WHERE work_order_id=?",missing);
        var first=dashboard("2026-10");assertEquals(1,first.get("completedCount").asInt());assertEquals(1,first.get("invalidTimeSampleCount").asInt());assertEquals(0,first.get("averageResponseSeconds").asDouble());assertTrue(first.get("averageRepairSeconds").isNull());
        long negative=order("COMPLETED",true);db.update("UPDATE repair_request SET first_submitted_at=? WHERE id=(SELECT request_id FROM work_order WHERE id=?)",time.instant().plusSeconds(1),negative);
        var both=dashboard("2026-10");assertEquals(2,both.get("sampleCount").asInt());assertEquals(2,both.get("invalidTimeSampleCount").asInt());assertTrue(both.get("averageResponseSeconds").isNull());assertTrue(both.get("averageRepairSeconds").isNull());
        var rows=details("metric=COMPLETED&month=2026-10").get("records").get("records");assertEquals(2,rows.size());
        for(var row:rows) {assertTrue(row.get("hasInvalidTime").asBoolean());if(row.get("id").asString().equals(Long.toString(missing))){assertTrue(row.get("repairSeconds").isNull());assertEquals(0,row.get("responseSeconds").asDouble());}else{assertTrue(row.get("responseSeconds").isNull());assertEquals(2,row.get("repairSeconds").asDouble());}}
    }
    @Test void strict_scope_filters_pagination_unicode_and_private_headers() throws Exception {
        setup();order("COMPLETED",false);order("PENDING_ACCEPT",true);
        for(String query:List.of("metric=CURRENT_STATE&month=2026-10","metric=CURRENT_STATE&date=2026-10-12","metric=CURRENT_STATE&equipmentId=1","metric=COMPLETED","metric=BAD&month=2026-10","metric=DAILY_COMPLETED&month=2026-10&date=2026-09-30","metric=COMPLETED&month=2026-10&status=COMPLETED","metric=EQUIPMENT_FREQUENCY&month=2026-10","metric=EQUIPMENT_FREQUENCY&month=2026-10&equipmentId=1&unlinkedEquipment=true","metric=CURRENT_STATE&page=0","metric=CURRENT_STATE&pageSize=1000","metric=CURRENT_STATE&extra=x"))assertEquals(400,manager.get("/api/statistics/details?"+query).statusCode(),query);
        for(String query:List.of("month=2026-13","month=2026-1","month=2026-10&month=2026-11","month=2026-10&metric=COMPLETED"))assertEquals(400,manager.get("/api/statistics/dashboard?"+query).statusCode(),query);
        var page=details("metric=CURRENT_STATE&page=2&pageSize=1&keyword="+java.net.URLEncoder.encode("演示",java.nio.charset.StandardCharsets.UTF_8));assertEquals(2,page.get("records").get("total").asInt());assertEquals(1,page.get("records").get("records").size());assertEquals(2,page.get("records").get("page").asInt());
        assertEquals(1,details("metric=CURRENT_STATE&status=PENDING_ACCEPT").get("records").get("total").asInt());
        var response=manager.get("/api/statistics/dashboard?month=2026-10");String cache=response.headers().firstValue("Cache-Control").orElse("");assertTrue(cache.contains("private"));assertTrue(cache.contains("no-store"));
    }
    @Test void all_reads_share_a_repeatable_read_snapshot_while_another_connection_commits() throws Exception {
        setup();long id=order("PENDING_ACCEPT",true);
        var tx=new TransactionTemplate(transactions);tx.setReadOnly(true);tx.setIsolationLevel(java.sql.Connection.TRANSACTION_REPEATABLE_READ);
        tx.executeWithoutResult(ignored->{
            var actor=new CurrentUser(admin.id,CurrentUser.Role.ADMIN,true,null,0);
            var before=statistics.dashboard(actor,"2026-10");assertEquals(1,before.stateCounts().get("PENDING_ACCEPT"));
            try(var writer=datasource.getConnection()) {
                cn.wzbc.maintenance.config.TestDatabaseGuard.validateConnection(writer,"test");
                try(var statement=writer.prepareStatement("UPDATE work_order SET status='REASSIGN_PENDING',current_assignment_id=NULL,assigned_technician_id=NULL WHERE id=?")) {statement.setLong(1,id);assertEquals(1,statement.executeUpdate());}
            } catch(Exception e){throw new RuntimeException(e);}
            var after=statistics.dashboard(actor,"2026-10");assertEquals(1,after.stateCounts().get("PENDING_ACCEPT"));assertEquals(0,after.stateCounts().get("REASSIGN_PENDING"));
            assertEquals(1,statistics.details(actor,"CURRENT_STATE",null,null,null,null,"PENDING_ACCEPT",1,20,null).records().total());
        });
        assertEquals(1,dashboard("2026-10").get("stateCounts").get("REASSIGN_PENDING").asInt());
    }
}
