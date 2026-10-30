package cn.wzbc.maintenance.workorder;

import cn.wzbc.maintenance.account.entity.UserEntity;
import cn.wzbc.maintenance.security.CurrentUser;
import cn.wzbc.maintenance.support.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class B05WorkflowIT extends B04HttpTestSupport {
    private String operation() { return UUID.randomUUID().toString(); }
    private record Saved(B04Browser browser,String action,String type,Map<String,Object> body,int version,int round) {}
    private void perform(List<Saved> saved,B04Browser browser,String order,String action,String type,Map<String,Object> fields,int version,int round) throws Exception {
        var body=new LinkedHashMap<>(fields); body.put("operationId",operation());
        var result=browser.post("/api/work-orders/"+order+"/"+action,body);
        assertEquals(200,result.statusCode(),result.body()); assertEquals(version,data(result).get("resultVersion").asInt()); assertEquals(round,data(result).get("resultRound").asInt());
        saved.add(new Saved(browser,action,type,body,version,round));
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void both_equipment_modes_refuse_reassign_replace_rework_and_recover_all_original_markers(boolean linked) throws Exception {
        var reporter=account(CurrentUser.Role.REPORTER,null); var admin=account(CurrentUser.Role.ADMIN,null);
        var first=account(CurrentUser.Role.TECHNICIAN,CurrentUser.ReviewStatus.APPROVED); var worker=account(CurrentUser.Role.TECHNICIAN,CurrentUser.ReviewStatus.APPROVED);
        var owner=b04Browser(); owner.login(reporter); var manager=b04Browser(); manager.login(admin); var former=b04Browser(); former.login(first); var tech=b04Browser(); tech.login(worker);
        var created=owner.post("/api/repair-requests",createBody(upload(owner),locationA,operation())); String request=data(created).get("resourceId").asString();
        var approval=new LinkedHashMap<String,Object>(Map.of("version",0,"technicianId",first.id.toString(),"confirmPreviouslyRefused",false,"operationId",operation()));
        Long eq=null;
        if(linked) { eq=new B04WorkOrderFixture(db,time.instant()).equipment(locationA); equipment.add(eq); approval.put("equipmentId",eq.toString()); }
        var assigned=manager.post("/api/repair-requests/"+request+"/approve-and-assign",approval); assertEquals(200,assigned.statusCode(),assigned.body()); String order=data(assigned).get("relatedResourceId").asString(); long orderId=Long.parseLong(order);
        assertEquals(eq,db.queryForObject("SELECT equipment_id FROM work_order WHERE id=?",Long.class,orderId));
        var saved=new ArrayList<Saved>();
        perform(saved,former,order,"refuse","REFUSED",Map.of("version",0,"reason","需要重新协调"),1,0);
        assertEquals(409,manager.post("/api/work-orders/"+order+"/assign",Map.of("version",1,"technicianId",first.id.toString(),"reason","再次联系","confirmPreviouslyRefused",false,"operationId",operation())).statusCode());
        perform(saved,manager,order,"assign","ASSIGNED",Map.of("version",1,"technicianId",first.id.toString(),"reason","已沟通后重派","confirmPreviouslyRefused",true),2,0);
        perform(saved,manager,order,"assign","ASSIGNED",Map.of("version",2,"technicianId",worker.id.toString(),"reason","接单前改派","confirmPreviouslyRefused",false),3,0);
        perform(saved,tech,order,"accept","ACCEPTED",Map.of("version",3),4,1);
        String acceptedAt=db.queryForObject("SELECT CAST(accepted_at AS CHAR) FROM work_order WHERE id=?",String.class,orderId);
        if(linked) assertEquals(200,manager.post("/api/equipment/"+eq+"/disable",Map.of("version",0,"operationId",operation())).statusCode());
        perform(saved,tech,order,"progress","PROGRESS_ADDED",Map.of("version",4,"round",1,"content","第一轮独立进度"),5,1);
        String image1=voucher(tech,order,1);
        perform(saved,tech,order,"submit-result","RESULT_SUBMITTED",Map.of("version",5,"round",1,"result","第一轮修理","attachmentIds",List.of(image1)),6,1);
        assertNull(db.queryForObject("SELECT completed_at FROM work_order WHERE id=?",java.sql.Timestamp.class,orderId));
        assertEquals(403,former.content(image1).statusCode()); assertEquals(200,owner.content(image1).statusCode()); assertEquals(200,manager.content(image1).statusCode()); assertArrayEquals(png(),tech.content(image1).body());
        var limited=data(former.get("/api/work-orders/"+order)); assertEquals("ASSIGNMENT_ONLY",limited.get("historyScope").asString()); assertEquals(0,limited.get("rounds").size()); assertTrue(limited.get("version").isNull()); assertEquals(0,data(former.get("/api/work-orders?scope=CURRENT")).get("total").asInt());
        perform(saved,owner,order,"acceptance","ACCEPTANCE",Map.of("version",6,"round",1,"passed",false,"reason","仍需调整"),7,2);
        assertEquals(409,tech.post("/api/work-orders/"+order+"/progress",Map.of("version",4,"round",2,"content","旧版本不得复用","operationId",operation())).statusCode());
        assertEquals(409,tech.post("/api/work-orders/"+order+"/submit-result",Map.of("version",7,"round",2,"result","不能复用旧图","attachmentIds",List.of(image1),"operationId",operation())).statusCode());
        // Same uploader, another real order: ownership alone must not allow binding.
        var other=owner.post("/api/repair-requests",createBody(upload(owner),locationA,operation())); String otherRequest=data(other).get("resourceId").asString();
        var otherAssigned=manager.post("/api/repair-requests/"+otherRequest+"/approve-and-assign",Map.of("version",0,"technicianId",worker.id.toString(),"confirmPreviouslyRefused",false,"operationId",operation())); String otherOrder=data(otherAssigned).get("relatedResourceId").asString();
        assertEquals(200,tech.post("/api/work-orders/"+otherOrder+"/accept",Map.of("version",0,"operationId",operation())).statusCode());
        String otherImage=voucher(tech,otherOrder,1);
        assertEquals(409,tech.post("/api/work-orders/"+order+"/submit-result",Map.of("version",7,"round",2,"result","跨工单无效","attachmentIds",List.of(otherImage),"operationId",operation())).statusCode());
        assertEquals("TEMPORARY",db.queryForObject("SELECT state FROM attachment_file WHERE id=?",String.class,Long.parseLong(otherImage)));
        String image2=voucher(tech,order,2);
        perform(saved,tech,order,"submit-result","RESULT_SUBMITTED",Map.of("version",7,"round",2,"result","第二轮新结果","attachmentIds",List.of(image2)),8,2);
        assertEquals(409,owner.post("/api/work-orders/"+order+"/acceptance",Map.of("version",8,"round",1,"passed",true,"operationId",operation())).statusCode());
        perform(saved,owner,order,"acceptance","ACCEPTANCE",Map.of("version",8,"round",2,"passed",true),9,2);
        int eventCount=db.queryForObject("SELECT COUNT(*) FROM work_order_event WHERE work_order_id=?",Integer.class,orderId);
        for(var original:saved) {
            var replay=original.browser.post("/api/work-orders/"+order+"/"+original.action,original.body); assertEquals(200,replay.statusCode(),replay.body());
            assertEquals(original.version,data(replay).get("resultVersion").asInt()); assertEquals(original.round,data(replay).get("resultRound").asInt());
            var lookup=data(original.browser.get("/api/operations/"+original.body.get("operationId")+"?operation="+original.type)); assertEquals("SUCCEEDED",lookup.get("kind").asString()); assertEquals(order,lookup.get("receipt").get("resourceId").asString()); assertEquals(original.version,lookup.get("receipt").get("resultVersion").asInt());
        }
        assertEquals(eventCount,db.queryForObject("SELECT COUNT(*) FROM work_order_event WHERE work_order_id=?",Integer.class,orderId));
        assertEquals("COMPLETED",db.queryForObject("SELECT status FROM work_order WHERE id=?",String.class,orderId)); assertNotNull(db.queryForObject("SELECT completed_at FROM work_order WHERE id=?",java.sql.Timestamp.class,orderId));
        assertEquals(acceptedAt,db.queryForObject("SELECT CAST(accepted_at AS CHAR) FROM work_order WHERE id=?",String.class,orderId)); assertEquals(worker.id,db.queryForObject("SELECT accepted_technician_id FROM work_order WHERE id=?",Long.class,orderId));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM work_order WHERE request_id=?",Integer.class,Long.parseLong(request))); assertEquals(3,db.queryForObject("SELECT COUNT(*) FROM work_order_assignment WHERE work_order_id=?",Integer.class,orderId)); assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM repair_round WHERE work_order_id=? AND acceptance_at IS NOT NULL",Integer.class,orderId));
        assertEquals(403,former.content(image2).statusCode()); assertEquals(200,tech.content(image2).statusCode());
    }
    @Test void linked_equipment_order_completes_with_real_voucher_and_persisted_receipts() throws Exception {
        UserEntity reporter=account(CurrentUser.Role.REPORTER,null), admin=account(CurrentUser.Role.ADMIN,null), worker=account(CurrentUser.Role.TECHNICIAN,CurrentUser.ReviewStatus.APPROVED);
        var owner=b04Browser(); owner.login(reporter);
        long request=Long.parseLong(data(owner.post("/api/repair-requests",createBody(upload(owner),locationA,operation()))).get("resourceId").asString());
        var fixture=new B04WorkOrderFixture(db,time.instant()); long equipmentId=fixture.equipment(locationA); equipment.add(equipmentId);
        long order=fixture.order(request,equipmentId,worker.id,admin.id,"PENDING_ACCEPT");
        var technician=b04Browser(); technician.login(worker);
        String acceptOp=operation(); var accepted=technician.post("/api/work-orders/"+order+"/accept",Map.of("version",0,"operationId",acceptOp));
        assertEquals(200,accepted.statusCode(),accepted.body());
        String voucher=voucher(technician,Long.toString(order),1);
        String submitOp=operation(); var submit=technician.post("/api/work-orders/"+order+"/submit-result",Map.of("version",1,"round",1,"result","设备已修复","attachmentIds",List.of(voucher),"operationId",submitOp));
        assertEquals(200,submit.statusCode(),submit.body());
        String acceptanceOp=operation(); var result=owner.post("/api/work-orders/"+order+"/acceptance",Map.of("version",2,"round",1,"passed",true,"operationId",acceptanceOp));
        assertEquals(200,result.statusCode(),result.body());
        assertEquals("COMPLETED",db.queryForObject("SELECT status FROM work_order WHERE id=?",String.class,order));
        assertEquals(equipmentId,db.queryForObject("SELECT equipment_id FROM work_order WHERE id=?",Long.class,order));
        assertEquals(3,db.queryForObject("SELECT COUNT(*) FROM work_order_event WHERE work_order_id=? AND operation_id IN (?,?,?)",Integer.class,order,acceptOp,submitOp,acceptanceOp));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM attachment_file WHERE id=? AND state='BOUND' AND target_work_order_id=? AND bound_round_id=(SELECT id FROM repair_round WHERE work_order_id=? AND round_no=1)",Integer.class,Long.parseLong(voucher),order,order));
    }
    @Test void no_equipment_two_rounds_keep_original_worker_and_complete_only_after_reporter_accepts() throws Exception {
        UserEntity reporter=account(CurrentUser.Role.REPORTER,null), admin=account(CurrentUser.Role.ADMIN,null), worker=account(CurrentUser.Role.TECHNICIAN,CurrentUser.ReviewStatus.APPROVED);
        var owner=b04Browser(); owner.login(reporter);
        long request=Long.parseLong(data(owner.post("/api/repair-requests",createBody(upload(owner),locationA,operation()))).get("resourceId").asString());
        var manager=b04Browser(); manager.login(admin);
        var approval=manager.post("/api/repair-requests/"+request+"/approve-and-assign",Map.of("version",0,"technicianId",worker.id.toString(),"confirmPreviouslyRefused",false,"operationId",operation()));
        assertEquals(200,approval.statusCode(),approval.body());
        String order=data(approval).get("relatedResourceId").asString();
        assertNull(db.queryForObject("SELECT equipment_id FROM work_order WHERE id=?",Long.class,Long.parseLong(order)));
        var technician=b04Browser(); technician.login(worker);
        assertEquals(200,technician.post("/api/work-orders/"+order+"/accept",Map.of("version",0,"operationId",operation())).statusCode());
        String firstAccepted=db.queryForObject("SELECT CAST(accepted_at AS CHAR) FROM work_order WHERE id=?",String.class,Long.parseLong(order));
        assertEquals(200,technician.post("/api/work-orders/"+order+"/progress",Map.of("version",1,"round",1,"content","检查电源线路","operationId",operation())).statusCode());
        String firstVoucher=voucher(technician,order,1);
        var submitted=technician.post("/api/work-orders/"+order+"/submit-result",Map.of("version",2,"round",1,"result","第一轮已修理","attachmentIds",List.of(firstVoucher),"operationId",operation()));
        assertEquals(200,submitted.statusCode(),submitted.body());
        assertEquals("PENDING_ACCEPTANCE",db.queryForObject("SELECT status FROM work_order WHERE id=?",String.class,Long.parseLong(order)));
        assertNull(db.queryForObject("SELECT completed_at FROM work_order WHERE id=?",java.sql.Timestamp.class,Long.parseLong(order)));
        var fail=owner.post("/api/work-orders/"+order+"/acceptance",Map.of("version",3,"round",1,"passed",false,"reason","仍有故障","operationId",operation()));
        assertEquals(200,fail.statusCode(),fail.body());
        assertEquals(2,db.queryForObject("SELECT current_round FROM work_order WHERE id=?",Integer.class,Long.parseLong(order)));
        assertEquals(409,owner.post("/api/work-orders/"+order+"/acceptance",Map.of("version",4,"round",1,"passed",true,"operationId",operation())).statusCode());
        String secondVoucher=voucher(technician,order,2);
        assertEquals(409,technician.post("/api/work-orders/"+order+"/submit-result",Map.of("version",4,"round",2,"result","旧图不应可用","attachmentIds",List.of(firstVoucher),"operationId",operation())).statusCode());
        var second=technician.post("/api/work-orders/"+order+"/submit-result",Map.of("version",4,"round",2,"result","第二轮已彻底修理","attachmentIds",List.of(secondVoucher),"operationId",operation()));
        assertEquals(200,second.statusCode(),second.body());
        var passed=owner.post("/api/work-orders/"+order+"/acceptance",Map.of("version",5,"round",2,"passed",true,"operationId",operation()));
        assertEquals(200,passed.statusCode(),passed.body());
        assertEquals("COMPLETED",db.queryForObject("SELECT status FROM work_order WHERE id=?",String.class,Long.parseLong(order)));
        assertEquals(firstAccepted,db.queryForObject("SELECT CAST(accepted_at AS CHAR) FROM work_order WHERE id=?",String.class,Long.parseLong(order)));
        assertEquals(worker.id,db.queryForObject("SELECT accepted_technician_id FROM work_order WHERE id=?",Long.class,Long.parseLong(order)));
        assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM repair_round WHERE work_order_id=?",Integer.class,Long.parseLong(order)));
        assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM attachment_file WHERE target_work_order_id=? AND state='BOUND'",Integer.class,Long.parseLong(order)));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM work_order WHERE request_id=?",Integer.class,request));
    }
    private String voucher(B04Browser browser,String order,int round) throws Exception {
        var upload=browser.upload(Map.of("operationId",operation(),"purpose","REPAIR_VOUCHER","workOrderId",order,"round",Integer.toString(round)),"repair.png","image/png",png(),true);
        assertEquals(201,upload.statusCode(),upload.body()); return data(upload).get("id").asString();
    }
}
