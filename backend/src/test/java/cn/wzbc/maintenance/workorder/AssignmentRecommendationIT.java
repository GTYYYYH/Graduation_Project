package cn.wzbc.maintenance.workorder;

import cn.wzbc.maintenance.account.entity.UserEntity;
import cn.wzbc.maintenance.security.CurrentUser;
import cn.wzbc.maintenance.support.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Real MySQL, registered actor-owned records and controllable server Clock. */
class AssignmentRecommendationIT extends B04HttpTestSupport {
    private UserEntity reporter,admin;
    private B04Browser manager,owner;
    private void setup() throws Exception {
        reporter=account(CurrentUser.Role.REPORTER,null); admin=account(CurrentUser.Role.ADMIN,null);
        manager=b04Browser();manager.login(admin);owner=b04Browser();owner.login(reporter);
    }
    private String request() throws Exception { return data(owner.post("/api/repair-requests",createBody(upload(owner),locationA,UUID.randomUUID().toString()))).get("resourceId").asString(); }
    private long order(UserEntity technician,String status) throws Exception {
        long req=Long.parseLong(request()); var fixture=new B04WorkOrderFixture(db,time.instant().minusSeconds(100));
        long eq=fixture.equipment(locationA);equipment.add(eq);
        return fixture.order(req,eq,technician.id,admin.id,status);
    }
    private tools.jackson.databind.JsonNode read(String req,String extra) throws Exception {
        var response=manager.get("/api/work-orders/assignment-candidates?context=FIRST_ASSIGN&requestId="+req+extra);
        assertEquals(200,response.statusCode(),response.body());return data(response);
    }
    private List<String> ids(tools.jackson.databind.JsonNode rows) { var result=new ArrayList<String>();for(var r:rows)result.add(r.get("id").asString());return result; }
    private String voucher(B04Browser tech,long order,int round) throws Exception {
        var image=tech.upload(Map.of("operationId",UUID.randomUUID().toString(),"purpose","REPAIR_VOUCHER","workOrderId",Long.toString(order),"round",Integer.toString(round)),"proof.png","image/png",png(),true);
        assertEquals(201,image.statusCode(),image.body());return data(image).get("id").asString();
    }
    @Test void same_load_fewer_completed_first() throws Exception {
        setup();var five=account(CurrentUser.Role.TECHNICIAN,CurrentUser.ReviewStatus.APPROVED);var one=account(CurrentUser.Role.TECHNICIAN,CurrentUser.ReviewStatus.APPROVED);
        String fiveCompletedId=five.id.toString(),oneCompletedId=one.id.toString();
        for(int i=0;i<5;i++)order(five,"COMPLETED");order(one,"COMPLETED");
        var response=read(request(),"");var rankedIds=ids(response.get("top5"));
        assertEquals(List.of(oneCompletedId, fiveCompletedId), rankedIds);
        java.nio.file.Files.writeString(java.nio.file.Path.of("../tests/evidence/B08-mysql-same-load.json"),json.writeValueAsString(Map.of("expectedIds",List.of(oneCompletedId,fiveCompletedId),"expectedTodayCompleted",List.of(1,5),"actual",response)));
        assertEquals(1,response.get("top5").get(0).get("todayCompleted").asInt());assertEquals(5,response.get("top5").get(1).get("todayCompleted").asInt());
    }
    @Test void weighted_stages_are_disjoint_and_legacy_orders_count_once() throws Exception {
        setup();var t=account(CurrentUser.Role.TECHNICIAN,CurrentUser.ReviewStatus.APPROVED);
        long w=order(t,"PENDING_ACCEPT"),i=order(t,"IN_PROGRESS"),r=order(t,"IN_PROGRESS"),a=order(t,"IN_PROGRESS");
        db.update("UPDATE work_order SET location_mode='LEGACY_TEXT',location_id=NULL,location_name_snapshot=NULL WHERE id=?",w);
        // Round 1 failed; current round 2 is processing, so only R counts.
        var old=time.instant().minusSeconds(60);
        db.update("UPDATE repair_round SET submitted_at=?,result_text='first',acceptance_passed=0,acceptance_reason='rework',accepted_by=?,acceptance_at=? WHERE work_order_id=?",old,reporter.id,old,r);
        long round=com.baomidou.mybatisplus.core.toolkit.IdWorker.getId();
        db.update("INSERT INTO repair_round(id,created_at,work_order_id,round_no,technician_id,started_at) VALUES(?,?,?,2,?,?)",round,old,r,t.id,old);
        db.update("UPDATE work_order SET current_round=2 WHERE id=?",r);
        db.update("UPDATE repair_round SET submitted_at=?,result_text='waiting' WHERE work_order_id=?",old,a);
        db.update("UPDATE work_order SET status='PENDING_ACCEPTANCE' WHERE id=?",a);
        for(int n=0;n<3;n++) db.update("INSERT INTO repair_progress(id,created_at,work_order_id,round_id,technician_id,content,recorded_at) VALUES(?,?,?,?,?,'progress',?)",com.baomidou.mybatisplus.core.toolkit.IdWorker.getId(),old,r,round,t.id,old);
        var row=read(request(),"").get("top5").get(0);
        for(String key:List.of("W","I","R","A"))assertEquals(1,row.get(key).asInt(),key);
        java.nio.file.Files.writeString(java.nio.file.Path.of("../tests/evidence/B08-mysql-weights.json"),json.writeValueAsString(Map.of("expected",Map.of("W",1,"I",1,"R",1,"A",1,"load",6.5,"todayCompleted",0),"actual",row)));
        assertEquals(6.5,row.get("load").asDouble());assertEquals(0,row.get("todayCompleted").asInt());
    }
    @Test void full_sort_precedes_paging_keyword_does_not_change_top5_and_high_load_is_eligible() throws Exception {
        setup();var people=new ArrayList<UserEntity>();for(int n=0;n<7;n++)people.add(account(CurrentUser.Role.TECHNICIAN,CurrentUser.ReviewStatus.APPROVED));
        // First ID has load, all other null-lastAssigned rows precede it, stable numeric IDs.
        order(people.get(0),"PENDING_ACCEPT");String req=request();var all=read(req,"");
        var expected=people.subList(1,7).stream().map(u->u.id.toString()).toList();
        assertEquals(expected.subList(0,5),ids(all.get("top5")));assertEquals(7,all.get("eligibleTotal").asInt());
        assertEquals(List.of(people.get(0).id.toString()),ids(read(req,"&page=4&pageSize=2").get("candidates").get("records")));
        var searched=read(req,"&keyword="+people.get(0).loginName);assertEquals(ids(all.get("top5")),ids(searched.get("top5")));assertEquals(1,searched.get("candidates").get("total").asInt());
        assertTrue(searched.get("candidates").get("records").get(0).get("eligible").asBoolean());
    }
    @Test void shanghai_day_is_half_open_and_completion_belongs_to_actual_worker() throws Exception {
        setup();var t=account(CurrentUser.Role.TECHNICIAN,CurrentUser.ReviewStatus.APPROVED);String req=request();
        long yesterday=order(t,"COMPLETED"),today=order(t,"COMPLETED"),tomorrow=order(t,"COMPLETED");
        // 2026-10-12 Shanghai [2026-10-11T16:00Z, 2026-10-12T16:00Z).
        for(var e:Map.of(yesterday,"2026-10-11T15:59:59.999Z",today,"2026-10-11T16:00:00Z",tomorrow,"2026-10-12T16:00:00Z").entrySet()) {
            var finish=Instant.parse(e.getValue());db.update("UPDATE work_order SET accepted_at=?,completed_at=? WHERE id=?",finish.minusSeconds(60),finish,e.getKey());
        }
        assertEquals(1,read(req,"").get("top5").get(0).get("todayCompleted").asInt());
        time.set(Instant.parse("2026-10-12T16:00:00Z"));manager.login(admin);var next=read(req,"");assertEquals("2026-10-13",next.get("businessDate").asString());assertEquals(1,next.get("top5").get(0).get("todayCompleted").asInt());
    }
    @Test void contexts_refusals_empty_states_and_permissions() throws Exception {
        setup();String req=request();assertEquals(0,read(req,"").get("eligibleTotal").asInt());
        var t=account(CurrentUser.Role.TECHNICIAN,CurrentUser.ReviewStatus.APPROVED);long id=order(t,"PENDING_ACCEPT");
        var changed=manager.get("/api/work-orders/assignment-candidates?context=CHANGE_BEFORE_ACCEPT&workOrderId="+id);assertEquals(0,data(changed).get("eligibleTotal").asInt());
        var worker=b04Browser();worker.login(t);assertEquals(200,worker.post("/api/work-orders/"+id+"/refuse",Map.of("version",0,"reason","registered refusal","operationId",UUID.randomUUID().toString())).statusCode());
        var refused=manager.get("/api/work-orders/assignment-candidates?context=REASSIGN&workOrderId="+id);assertEquals(200,refused.statusCode(),refused.body());
        assertEquals(1,data(refused).get("eligibleTotal").asInt());assertEquals(0,data(refused).get("preferredTotal").asInt());assertEquals(0,data(refused).get("top5").size());
        var row=data(refused).get("candidates").get("records").get(0);assertEquals(1,row.get("refusal").size());assertFalse(row.get("lastAssignedAt").isNull());assertEquals(0,row.get("load").asDouble());
        for(String q:List.of("context=FIRST_ASSIGN","context=FIRST_ASSIGN&requestId="+req+"&workOrderId="+id,"context=REASSIGN&requestId="+req,"context=BAD","context=FIRST_ASSIGN&requestId="+req+"&other=x")) assertEquals(400,manager.get("/api/work-orders/assignment-candidates?"+q).statusCode(),q);
        assertEquals(403,owner.get("/api/work-orders/assignment-candidates?context=FIRST_ASSIGN&requestId="+req).statusCode());
        assertEquals(409,manager.get("/api/work-orders/assignment-candidates?context=CHANGE_BEFORE_ACCEPT&workOrderId="+id).statusCode());
        db.update("UPDATE user_account SET enabled=0 WHERE id=?",t.id);assertEquals(0,read(req,"").get("eligibleTotal").asInt());
        var rejected=manager.post("/api/repair-requests/"+req+"/approve-and-assign",Map.of("version",0,"technicianId",t.id.toString(),"confirmPreviouslyRefused",false,"operationId",UUID.randomUUID().toString()));assertEquals(409,rejected.statusCode(),rejected.body());
    }
    @Test void multiple_rounds_vouchers_and_assignments_never_inflate_actual_completion() throws Exception {
        setup();var actual=account(CurrentUser.Role.TECHNICIAN,CurrentUser.ReviewStatus.APPROVED);var former=account(CurrentUser.Role.TECHNICIAN,CurrentUser.ReviewStatus.APPROVED);
        long id=order(actual,"IN_PROGRESS");var fixture=new B04WorkOrderFixture(db,time.instant().minusSeconds(100));
        long submission=db.queryForObject("SELECT current_submission_id FROM repair_request WHERE id=(SELECT request_id FROM work_order WHERE id=?)",Long.class,id);
        fixture.formerBeforeCurrent(id,former.id,admin.id,submission);
        var tech=b04Browser();tech.login(actual);String req=request();
        for(int round=1;round<=2;round++) {
            int before=round==1?0:2;
            var result=tech.post("/api/work-orders/"+id+"/submit-result",Map.of("version",before,"round",round,"result","本轮新结果"+round,"attachmentIds",List.of(voucher(tech,id,round),voucher(tech,id,round)),"operationId",UUID.randomUUID().toString()));assertEquals(200,result.statusCode(),result.body());
            var waiting=read(req,"").get("candidates").get("records");
            for(var row:waiting) {assertEquals(0,row.get("todayCompleted").asInt());if(row.get("id").asString().equals(actual.id.toString())) {assertEquals(1,row.get("A").asInt());assertEquals(0,row.get("R").asInt());}}
            var decision=new HashMap<String,Object>(Map.of("version",before+1,"round",round,"passed",round==2,"operationId",UUID.randomUUID().toString()));if(round==1)decision.put("reason","第一轮未解决");
            var accepted=owner.post("/api/work-orders/"+id+"/acceptance",decision);assertEquals(200,accepted.statusCode(),accepted.body());
            if(round==1) {var rows=read(req,"").get("candidates").get("records");for(var row:rows)if(row.get("id").asString().equals(actual.id.toString())) {assertEquals(1,row.get("R").asInt());assertEquals(0,row.get("I").asInt());assertEquals(3,row.get("load").asDouble());}}
        }
        var rows=read(req,"").get("candidates").get("records");for(var row:rows) {assertEquals(row.get("id").asString().equals(actual.id.toString())?1:0,row.get("todayCompleted").asInt());assertEquals(0,row.get("load").asDouble());}
        assertEquals(4,db.queryForObject("SELECT COUNT(*) FROM attachment_file WHERE bound_round_id IN (SELECT id FROM repair_round WHERE work_order_id=?)",Integer.class,id));
        // Completed/in-progress/rework contexts cannot be opened or used to reassign.
        for(String context:List.of("CHANGE_BEFORE_ACCEPT","REASSIGN"))assertEquals(409,manager.get("/api/work-orders/assignment-candidates?context="+context+"&workOrderId="+id).statusCode());
        assertEquals(409,manager.post("/api/work-orders/"+id+"/assign",Map.of("version",4,"technicianId",former.id.toString(),"reason","接单后不得改派","confirmPreviouslyRefused",true,"operationId",UUID.randomUUID().toString())).statusCode());
    }
    @Test void null_last_assignment_then_oldest_assignment_then_numeric_id_and_read_only() throws Exception {
        setup();var nine=account(CurrentUser.Role.TECHNICIAN,CurrentUser.ReviewStatus.APPROVED);var hundred=account(CurrentUser.Role.TECHNICIAN,CurrentUser.ReviewStatus.APPROVED);
        db.update("UPDATE user_account SET id=9 WHERE id=?",nine.id);owned.set(owned.indexOf(nine.id),9L);nine.id=9L;
        db.update("UPDATE user_account SET id=100 WHERE id=?",hundred.id);owned.set(owned.indexOf(hundred.id),100L);hundred.id=100L;
        var old=account(CurrentUser.Role.TECHNICIAN,CurrentUser.ReviewStatus.APPROVED);var recent=account(CurrentUser.Role.TECHNICIAN,CurrentUser.ReviewStatus.APPROVED);
        order(recent,"COMPLETED");order(old,"COMPLETED");
        // All four candidates must have equal L=0,D=0 to isolate null-first and numeric-ID ties.
        db.update("UPDATE work_order SET accepted_at=accepted_at-INTERVAL 1 DAY,completed_at=completed_at-INTERVAL 1 DAY WHERE accepted_technician_id IN (?,?)",old.id,recent.id);
        db.update("UPDATE work_order_assignment SET assigned_at=assigned_at-INTERVAL 10 SECOND WHERE technician_id=?",old.id);
        String req=request();int events=db.queryForObject("SELECT COUNT(*) FROM work_order_event",Integer.class),orders=db.queryForObject("SELECT COUNT(*) FROM work_order",Integer.class);
        // D is equal within each pair; null first, numeric 9 before 100, then older assignment.
        var response=read(req,"");assertEquals(List.of("9","100",old.id.toString(),recent.id.toString()),ids(response.get("top5")));
        assertEquals(events,db.queryForObject("SELECT COUNT(*) FROM work_order_event",Integer.class));assertEquals(orders,db.queryForObject("SELECT COUNT(*) FROM work_order",Integer.class));
        java.nio.file.Path evidence=java.nio.file.Path.of("../tests/evidence/B08-mysql-hand-calculation.json");
        java.nio.file.Files.writeString(evidence,json.writeValueAsString(Map.of("clock",time.instant().toString(),"expectedIds",List.of("9","100",old.id.toString(),recent.id.toString()),"actual",response,"readOnlyEventsUnchanged",true)));
    }
}
