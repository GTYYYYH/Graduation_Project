package cn.wzbc.maintenance.repairrequest;

import cn.wzbc.maintenance.account.entity.UserEntity;
import cn.wzbc.maintenance.security.CurrentUser;
import cn.wzbc.maintenance.support.B04HttpTestSupport;
import cn.wzbc.maintenance.equipment.mapper.EquipmentMapper;
import cn.wzbc.maintenance.workorder.mapper.WorkOrderMapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

/** Actual Cookie/CSRF HTTP, MySQL transactions/constraints and scoped real attachments. */
class ApproveAndAssignIT extends B04HttpTestSupport {
    @MockitoSpyBean WorkOrderMapper orderMapper;
    @Autowired EquipmentMapper deviceMapper;

    @Test void technician_only_approval_commits_unlinked_order_and_readable_material_with_idempotent_replay() throws Exception {
        var reporter=logged(account(CurrentUser.Role.REPORTER,null)); var admin=logged(account(CurrentUser.Role.ADMIN,null)); var worker=account(CurrentUser.Role.TECHNICIAN,CurrentUser.ReviewStatus.APPROVED); var technician=logged(worker); var outsider=logged(account(CurrentUser.Role.REPORTER,null));
        String request=create(reporter,locationA), path="/api/repair-requests/"+request+"/approve-and-assign";
        var dto=new LinkedHashMap<String,Object>(Map.of("technicianId",worker.id.toString(),"version",0,"operationId",UUID.randomUUID().toString(),"confirmPreviouslyRefused",false));
        var response=admin.post(path,dto); assertEquals(200,response.statusCode(),response.body());
        String order=data(response).get("relatedResourceId").asString();
        assertNull(db.queryForObject("SELECT equipment_id FROM work_order WHERE id=?",Long.class,Long.parseLong(order)));
        assertNull(db.queryForObject("SELECT matched_equipment_id FROM repair_request WHERE id=?",Long.class,Long.parseLong(request)));
        for(var reader:List.of(admin,reporter,technician)) {
            var read=reader.get("/api/work-orders/"+order); assertEquals(200,read.statusCode(),read.body()); var detail=data(read);
            assertTrue(detail.get("equipment").isNull()); assertEquals("PENDING_ACCEPT",detail.get("status").asString()); assertEquals(0,detail.get("round").asInt()); assertEquals("用户原文地址",detail.get("requestMaterial").get("addressText").asString());
            String image=detail.get("requestMaterial").get("attachments").get(0).get("id").asString(); assertEquals(200,reader.get("/api/attachments/"+image+"/content").statusCode());
            var list=reader.get("/api/work-orders?keyword=WO-"+order); assertEquals(200,list.statusCode(),list.body()); assertEquals(1,data(list).get("total").asInt()); assertTrue(data(list).get("records").get(0).get("equipment").isNull());
        }
        assertEquals(403,outsider.get("/api/work-orders/"+order).statusCode()); assertEquals(data(response),data(admin.post(path,dto)));
        dto.put("technicianId",account(CurrentUser.Role.TECHNICIAN,CurrentUser.ReviewStatus.APPROVED).id.toString()); assertEquals("OPERATION_CONTENT_MISMATCH",code(admin.post(path,dto)));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM work_order_assignment WHERE work_order_id=?",Integer.class,Long.parseLong(order))); assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM repair_request_event WHERE request_id=? AND event_type='APPROVED_ASSIGNED'",Integer.class,Long.parseLong(request)));
    }

    @Test void technician_only_approval_still_checks_qualification_version_permissions_and_rolls_back_on_failure() throws Exception {
        var reporter=logged(account(CurrentUser.Role.REPORTER,null)); var manager=account(CurrentUser.Role.ADMIN,null); var admin=logged(manager); var worker=account(CurrentUser.Role.TECHNICIAN,CurrentUser.ReviewStatus.APPROVED);
        String request=create(reporter,locationA),path="/api/repair-requests/"+request+"/approve-and-assign";
        var dto=new LinkedHashMap<String,Object>(Map.of("technicianId",worker.id.toString(),"version",1,"operationId",UUID.randomUUID().toString(),"confirmPreviouslyRefused",false));
        assertEquals("VERSION_CONFLICT",code(admin.post(path,dto))); pending(request); dto.put("version",0);
        assertEquals(403,reporter.post(path,dto).statusCode()); var anonymous=b04Browser(); anonymous.csrf(); assertEquals(401,anonymous.post(path,dto).statusCode());
        assertEquals(403,admin.request("POST",path,dto,null).statusCode()); pending(request);
        db.update("UPDATE user_account SET enabled=0 WHERE id=?",worker.id); assertEquals("QUALIFICATION_CHANGED",code(admin.post(path,dto))); pending(request);
        db.update("UPDATE user_account SET enabled=1 WHERE id=?",worker.id);
        doThrow(new IllegalStateException("test-only transaction rollback")).when(orderMapper).insertAssignment(any());
        assertEquals(500,admin.post(path,dto).statusCode()); pending(request);
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM work_order_event WHERE actor_user_id=?",Integer.class,manager.id));
    }

    @Test void concurrent_technician_only_replay_creates_one_order_and_another_request_can_also_be_unlinked() throws Exception {
        var reporter=logged(account(CurrentUser.Role.REPORTER,null)); var manager=account(CurrentUser.Role.ADMIN,null); var a=logged(manager); var b=logged(manager); var worker=account(CurrentUser.Role.TECHNICIAN,CurrentUser.ReviewStatus.APPROVED);
        String request=create(reporter,locationA),second=create(reporter,locationA),path="/api/repair-requests/"+request+"/approve-and-assign";
        var dto=new LinkedHashMap<String,Object>(Map.of("technicianId",worker.id.toString(),"version",0,"operationId",UUID.randomUUID().toString(),"confirmPreviouslyRefused",false)); var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var one=pool.submit(() -> {start.await(); return a.post(path,dto);}); var two=pool.submit(() -> {start.await(); return b.post(path,dto);}); start.countDown();
            var first=one.get(20,TimeUnit.SECONDS); var replay=two.get(20,TimeUnit.SECONDS); assertEquals(200,first.statusCode(),first.body()); assertEquals(200,replay.statusCode(),replay.body()); assertEquals(data(first),data(replay));
        }
        dto.put("operationId",UUID.randomUUID().toString()); var response=a.post("/api/repair-requests/"+second+"/approve-and-assign",dto); assertEquals(200,response.statusCode(),response.body());
        assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM work_order WHERE request_id IN (?,?) AND equipment_id IS NULL",Integer.class,Long.parseLong(request),Long.parseLong(second)));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM work_order WHERE request_id=?",Integer.class,Long.parseLong(request)));
    }

    private B04Browser logged(UserEntity user) throws Exception { var b=b04Browser(); b.login(user); return b; }
    private String create(B04Browser reporter,long location) throws Exception {
        var response=reporter.post("/api/repair-requests",createBody(upload(reporter),location,UUID.randomUUID().toString()));
        assertEquals(201,response.statusCode(),response.body()); return data(response).get("resourceId").asString();
    }
    private long device(Long location,String facility,boolean enabled) {
        long id=IdWorker.getId(); equipment.add(id);
        db.update("INSERT INTO equipment(id,created_at,equipment_no,name,location_id,address_text,facility_type,enabled,version,updated_at) VALUES(?,?,?,'具体审核设备',?,'独立台账地址',?,?,0,?)",id,time.instant(),"EQ-"+id,location,facility,enabled,time.instant()); return id;
    }
    private Map<String,Object> approve(long equipmentId,long technician,int version,String operation) {
        return new LinkedHashMap<>(Map.of("equipmentId",Long.toString(equipmentId),"technicianId",Long.toString(technician),"version",version,"operationId",operation,"confirmPreviouslyRefused",false));
    }
    private HttpResponse<String> approve(B04Browser admin,String request,long equipmentId,long technician) throws Exception {
        return admin.post("/api/repair-requests/"+request+"/approve-and-assign",approve(equipmentId,technician,0,UUID.randomUUID().toString()));
    }
    private Map<String,Object> close(long equipmentId,String reason) {
        return new LinkedHashMap<>(Map.of("equipmentId",Long.toString(equipmentId),"version",0,"operationId",UUID.randomUUID().toString(),"reason",reason));
    }
    private void pending(String request) {
        long id=Long.parseLong(request);
        var current=db.queryForMap("SELECT status,version,matched_equipment_id FROM repair_request WHERE id=?",id);
        assertEquals("PENDING_REVIEW",current.get("status")); assertEquals(0,((Number)current.get("version")).intValue()); assertNull(current.get("matched_equipment_id"));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM work_order WHERE request_id=?",Integer.class,id));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM repair_request_event WHERE request_id=? AND event_type IN ('APPROVED_ASSIGNED','DUPLICATE_CLOSED')",Integer.class,id));
    }

    @Test void legacy_order_keeps_null_location() throws Exception {
        var reporter=logged(account(CurrentUser.Role.REPORTER,null)); var admin=logged(account(CurrentUser.Role.ADMIN,null)); var worker=account(CurrentUser.Role.TECHNICIAN,CurrentUser.ReviewStatus.APPROVED);
        String request=create(reporter,locationA); long eq=device(locationA,"AIR_CONDITIONER",true);
        // Compatibility is created only by server-side SQL over this fixture's IDs.
        db.update("UPDATE repair_request SET location_mode='LEGACY_TEXT',location_id=NULL,location_name_snapshot=NULL WHERE id=?",Long.parseLong(request));
        db.update("UPDATE repair_request_submission SET location_mode='LEGACY_TEXT',location_id=NULL,location_name_snapshot=NULL WHERE request_id=?",Long.parseLong(request));
        var dto=approve(eq,worker.id,0,UUID.randomUUID().toString()); String path="/api/repair-requests/"+request+"/approve-and-assign";
        var first=admin.post(path,dto); assertEquals(200,first.statusCode(),first.body()); assertEquals(data(first),data(admin.post(path,dto)));
        Long legacyOrderLocationId=db.queryForObject("SELECT location_id FROM work_order WHERE request_id=?",Long.class,Long.parseLong(request));
        int orderCountAfterRetry=db.queryForObject("SELECT COUNT(*) FROM work_order WHERE request_id=?",Integer.class,Long.parseLong(request));
        assertNull(legacyOrderLocationId); assertEquals(1,orderCountAfterRetry);
        assertEquals("LEGACY_TEXT",db.queryForObject("SELECT location_mode FROM work_order WHERE request_id=?",String.class,Long.parseLong(request)));
        assertNull(db.queryForObject("SELECT location_name_snapshot FROM work_order WHERE request_id=?",String.class,Long.parseLong(request)));
        String order=data(first).get("relatedResourceId").asString(); var detail=data(admin.get("/api/work-orders/"+order)); assertTrue(detail.get("location").isNull()); assertEquals("用户原文地址",detail.get("requestMaterial").get("addressText").asString());
    }

    @Test void legacy_device_without_location_does_not_bind_request_to_3d() throws Exception {
        var reporter=logged(account(CurrentUser.Role.REPORTER,null)); var admin=logged(account(CurrentUser.Role.ADMIN,null)); var worker=account(CurrentUser.Role.TECHNICIAN,CurrentUser.ReviewStatus.APPROVED);
        String request=create(reporter,locationA); long eq=device(null,"AIR_CONDITIONER",true);
        db.update("UPDATE repair_request SET location_mode='LEGACY_TEXT',location_id=NULL,location_name_snapshot=NULL WHERE id=?",Long.parseLong(request));
        db.update("UPDATE repair_request_submission SET location_mode='LEGACY_TEXT',location_id=NULL,location_name_snapshot=NULL WHERE request_id=?",Long.parseLong(request));
        var response=approve(admin,request,eq,worker.id); assertEquals(200,response.statusCode(),response.body());
        assertNull(db.queryForObject("SELECT location_id FROM work_order WHERE request_id=?",Long.class,Long.parseLong(request)));
        assertNull(db.queryForObject("SELECT location_id FROM equipment WHERE id=?",Long.class,eq));
    }

    @Test void structured_request_rejects_different_and_missing_device_location() throws Exception {
        var reporter=logged(account(CurrentUser.Role.REPORTER,null)); var admin=logged(account(CurrentUser.Role.ADMIN,null)); var worker=account(CurrentUser.Role.TECHNICIAN,CurrentUser.ReviewStatus.APPROVED);
        String request=create(reporter,locationA);
        for(Long location:Arrays.asList(locationB,null)) {
            var response=approve(admin,request,device(location,"AIR_CONDITIONER",true),worker.id);
            assertEquals(409,response.statusCode(),response.body()); assertEquals("LOCATION_MISMATCH",code(response)); pending(request);
            if(location!=null) { String message=json.readTree(response.body()).get("message").asString(); assertTrue(message.contains("101") && message.contains("102"),"位置冲突应明确告知申请与台账的双方位置: "+message); }
        }
        assertEquals(locationA,db.queryForObject("SELECT location_id FROM repair_request WHERE id=?",Long.class,Long.parseLong(request)));
    }

    @Test void disabled_or_out_of_scope_device_cannot_change_request() throws Exception {
        var reporter=logged(account(CurrentUser.Role.REPORTER,null)); var admin=logged(account(CurrentUser.Role.ADMIN,null)); var worker=account(CurrentUser.Role.TECHNICIAN,CurrentUser.ReviewStatus.APPROVED);
        String request=create(reporter,locationA);
        var disabled=approve(admin,request,device(locationA,"AIR_CONDITIONER",false),worker.id);
        assertEquals(409,disabled.statusCode(),disabled.body()); assertEquals("EQUIPMENT_DISABLED",code(disabled)); pending(request);
        for(long eq:List.of(device(locationA,"PROJECTOR",true),device(observation,"AIR_CONDITIONER",true))) {
            var response=approve(admin,request,eq,worker.id); assertEquals(400,response.statusCode(),response.body()); assertEquals("OUT_OF_SCOPE_EQUIPMENT",code(response)); pending(request);
        }
    }

    @Test void assignment_requires_current_qualified_enabled_technician() throws Exception {
        var reporter=logged(account(CurrentUser.Role.REPORTER,null)); var admin=logged(account(CurrentUser.Role.ADMIN,null)); String request=create(reporter,locationA); long eq=device(locationA,"AIR_CONDITIONER",true);
        var pendingWorker=account(CurrentUser.Role.TECHNICIAN,CurrentUser.ReviewStatus.PENDING); var rejected=account(CurrentUser.Role.TECHNICIAN,CurrentUser.ReviewStatus.REJECTED); var disabled=account(CurrentUser.Role.TECHNICIAN,CurrentUser.ReviewStatus.APPROVED); db.update("UPDATE user_account SET enabled=0 WHERE id=?",disabled.id);
        for(long target:List.of(pendingWorker.id,rejected.id,disabled.id,account(CurrentUser.Role.REPORTER,null).id,account(CurrentUser.Role.ADMIN,null).id,IdWorker.getId())) {
            var response=approve(admin,request,eq,target); assertEquals(409,response.statusCode(),response.body()); assertEquals("QUALIFICATION_CHANGED",code(response)); pending(request);
        }
    }

    @Test void approval_commits_order_assignment_both_events_and_receipt_together() throws Exception {
        var reporting=account(CurrentUser.Role.REPORTER,null); var managing=account(CurrentUser.Role.ADMIN,null); var worker=account(CurrentUser.Role.TECHNICIAN,CurrentUser.ReviewStatus.APPROVED);
        var reporter=logged(reporting); var admin=logged(managing); var technician=logged(worker); var other=logged(account(CurrentUser.Role.REPORTER,null)); var otherTech=logged(account(CurrentUser.Role.TECHNICIAN,CurrentUser.ReviewStatus.APPROVED));
        String request=create(reporter,locationA); long eq=device(locationA,"AIR_CONDITIONER",true); var before=data(admin.get("/api/repair-requests/"+request));
        assertTrue(before.get("allowedActions").toString().contains("APPROVE_AND_ASSIGN")); assertTrue(before.get("workOrder").isNull());
        var dto=approve(eq,worker.id,0,UUID.randomUUID().toString()); var response=admin.post("/api/repair-requests/"+request+"/approve-and-assign",dto); assertEquals(200,response.statusCode(),response.body());
        var receipt=data(response); String order=receipt.get("relatedResourceId").asString(); assertEquals("REPAIR_REQUEST",receipt.get("resourceType").asString()); assertEquals(request,receipt.get("resourceId").asString()); assertEquals(1,receipt.get("resultVersion").asInt()); assertEquals(0,receipt.get("resultRound").asInt()); assertEquals("WORK_ORDER",receipt.get("relatedResourceType").asString());
        var current=db.queryForMap("SELECT * FROM work_order WHERE id=?",Long.parseLong(order)); assertEquals("PENDING_ACCEPT",current.get("status")); assertEquals(0,((Number)current.get("current_round")).intValue()); assertEquals(0,((Number)current.get("version")).intValue()); assertEquals(worker.id,((Number)current.get("assigned_technician_id")).longValue()); assertNull(current.get("accepted_technician_id")); assertNull(current.get("accepted_at")); assertNull(current.get("completed_at"));
        var assignment=db.queryForMap("SELECT * FROM work_order_assignment WHERE id=?",current.get("current_assignment_id")); assertEquals("PENDING",assignment.get("outcome")); assertEquals(1,((Number)assignment.get("assignment_no")).intValue()); assertEquals(managing.id,((Number)assignment.get("assigned_by")).longValue()); assertEquals(before.get("currentSubmission").get("id").asString(),assignment.get("visible_submission_id").toString());
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM repair_request_event WHERE request_id=? AND event_type='APPROVED_ASSIGNED' AND operation_type='APPROVE_ASSIGN'",Integer.class,Long.parseLong(request)));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM work_order_event WHERE work_order_id=? AND event_type='FIRST_ASSIGNED' AND operation_id IS NULL",Integer.class,Long.parseLong(order)));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM repair_round WHERE work_order_id=?",Integer.class,Long.parseLong(order)));
        var detail=data(admin.get("/api/repair-requests/"+request)); assertEquals("APPROVED",detail.get("status").asString()); assertEquals(order,detail.get("workOrder").get("id").asString()); assertEquals(eq+"",detail.get("matchedEquipment").get("id").asString()); assertEquals("[\"VIEW_LINKED_ORDER\"]",detail.get("allowedActions").toString()); assertEquals(before.get("currentSubmission"),detail.get("currentSubmission"));
        var orderDetail=data(technician.get("/api/work-orders/"+order)); assertEquals("PENDING_ACCEPT",orderDetail.get("status").asString()); assertEquals("FULL",orderDetail.get("historyScope").asString()); assertEquals("[\"ACCEPT\",\"REFUSE\"]",orderDetail.get("allowedActions").toString()); assertTrue(orderDetail.get("rounds").isEmpty()); assertEquals(1,orderDetail.get("requestMaterial").get("attachments").size());
        assertEquals(200,reporter.get("/api/work-orders/"+order).statusCode()); assertEquals(403,other.get("/api/work-orders/"+order).statusCode()); assertEquals(403,otherTech.get("/api/work-orders/"+order).statusCode()); assertEquals(403,other.content(orderDetail.get("requestMaterial").get("attachments").get(0).get("id").asString()).statusCode());
        assertEquals(receipt,data(admin.get("/api/operations/"+dto.get("operationId")+"?operation=APPROVE_ASSIGN")).get("receipt")); assertEquals("NOT_CONFIRMED",data(other.get("/api/operations/"+dto.get("operationId")+"?operation=APPROVE_ASSIGN")).get("kind").asString());
        assertEquals(200,technician.post("/api/work-orders/"+order+"/accept",Map.of("version",0,"operationId",UUID.randomUUID().toString())).statusCode());
    }

    @Test void original_operation_replays_after_state_or_qualification_changes_but_different_content_conflicts() throws Exception {
        var reporter=logged(account(CurrentUser.Role.REPORTER,null)); var admin=logged(account(CurrentUser.Role.ADMIN,null)); var worker=account(CurrentUser.Role.TECHNICIAN,CurrentUser.ReviewStatus.APPROVED); String request=create(reporter,locationA); long eq=device(locationA,"AIR_CONDITIONER",true);
        var dto=approve(eq,worker.id,0,UUID.randomUUID().toString()); String path="/api/repair-requests/"+request+"/approve-and-assign"; var first=admin.post(path,dto); assertEquals(200,first.statusCode(),first.body());
        assertEquals(200,admin.post("/api/equipment/"+eq+"/disable",Map.of("version",0,"operationId",UUID.randomUUID().toString())).statusCode()); db.update("UPDATE user_account SET enabled=0 WHERE id=?",worker.id);
        assertEquals(data(first),data(admin.post(path,dto))); assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM work_order WHERE request_id=?",Integer.class,Long.parseLong(request)));
        var changed=new LinkedHashMap<>(dto); changed.put("confirmPreviouslyRefused",true); var conflict=admin.post(path,changed); assertEquals(409,conflict.statusCode()); assertEquals("OPERATION_CONTENT_MISMATCH",code(conflict));
        changed=new LinkedHashMap<>(dto); changed.put("operationId",UUID.randomUUID().toString()); var stale=admin.post(path,changed); assertEquals(409,stale.statusCode()); assertEquals("VERSION_CONFLICT",code(stale));
    }

    @Test void forced_failure_after_real_assignment_writes_rolls_back_all_records() throws Exception {
        var reporter=logged(account(CurrentUser.Role.REPORTER,null)); var admin=logged(account(CurrentUser.Role.ADMIN,null)); var worker=account(CurrentUser.Role.TECHNICIAN,CurrentUser.ReviewStatus.APPROVED); String request=create(reporter,locationA); long eq=device(locationA,"AIR_CONDITIONER",true);
        // Only the failure boundary is injected; preceding inserts use the real mapper/database.
        doThrow(new IllegalStateException("isolated T028 event failure")).when(orderMapper).insertEvent(any());
        var dto=approve(eq,worker.id,0,UUID.randomUUID().toString()); var response=admin.post("/api/repair-requests/"+request+"/approve-and-assign",dto); assertEquals(500,response.statusCode(),response.body()); assertEquals("INTERNAL_ERROR",code(response)); pending(request);
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM work_order_assignment WHERE technician_id=?",Integer.class,worker.id)); assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM work_order_event WHERE actor_user_id IN ("+marks(owned.size())+")",Integer.class,owned.toArray()));
        assertEquals("NOT_CONFIRMED",data(admin.get("/api/operations/"+dto.get("operationId")+"?operation=APPROVE_ASSIGN")).get("kind").asString());
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM attachment_file a JOIN repair_request_submission s ON s.id=a.bound_submission_id WHERE s.request_id=? AND a.state='BOUND'",Integer.class,Long.parseLong(request)));
    }

    @Test void simultaneous_requests_for_same_device_create_at_most_one_order() throws Exception {
        var reporterA=logged(account(CurrentUser.Role.REPORTER,null)); var reporterB=logged(account(CurrentUser.Role.REPORTER,null)); var adminA=logged(account(CurrentUser.Role.ADMIN,null)); var adminB=logged(account(CurrentUser.Role.ADMIN,null)); var worker=account(CurrentUser.Role.TECHNICIAN,CurrentUser.ReviewStatus.APPROVED);
        String a=create(reporterA,locationA),b=create(reporterB,locationA); long eq=device(locationA,"AIR_CONDITIONER",true); var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var x=pool.submit(() -> { start.await(); return approve(adminA,a,eq,worker.id); }); var y=pool.submit(() -> { start.await(); return approve(adminB,b,eq,worker.id); }); start.countDown();
            var one=x.get(20,TimeUnit.SECONDS); var two=y.get(20,TimeUnit.SECONDS); assertEquals(List.of(200,409),java.util.stream.Stream.of(one.statusCode(),two.statusCode()).sorted().toList(),one.body()+two.body());
            var rejected=one.statusCode()==409?one:two; assertEquals("EQUIPMENT_HAS_UNFINISHED_ORDER",code(rejected)); pending(one.statusCode()==409?a:b);
        }
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM work_order WHERE equipment_id=?",Integer.class,eq)); assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM work_order_assignment WHERE technician_id=?",Integer.class,worker.id));
    }

    @Test void generated_unique_constraint_rejects_another_unfinished_order() throws Exception {
        var reporter=logged(account(CurrentUser.Role.REPORTER,null)); var admin=logged(account(CurrentUser.Role.ADMIN,null)); var worker=account(CurrentUser.Role.TECHNICIAN,CurrentUser.ReviewStatus.APPROVED); String first=create(reporter,locationA),second=create(reporter,locationA); long eq=device(locationA,"AIR_CONDITIONER",true);
        var response=approve(admin,first,eq,worker.id); assertEquals(200,response.statusCode(),response.body()); long duplicate=IdWorker.getId();
        var failure=assertThrows(DataIntegrityViolationException.class,() -> new TransactionTemplate(transactions).execute(status -> db.update("INSERT INTO work_order(id,created_at,work_order_no,request_id,equipment_id,reporter_id,location_mode,location_id,location_name_snapshot,status,version,current_round,updated_at,assigned_technician_id) SELECT ?,created_at,?, ?,equipment_id,reporter_id,location_mode,location_id,location_name_snapshot,status,0,0,updated_at,assigned_technician_id FROM work_order WHERE request_id=?",duplicate,"WO-"+duplicate,Long.parseLong(second),Long.parseLong(first))));
        assertTrue(failure.getMostSpecificCause().getMessage().contains("uk_order_unfinished_equipment"),failure.getMostSpecificCause().getMessage()); pending(second);
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM work_order WHERE equipment_id=?",Integer.class,eq));
    }

    @Test void equipment_lock_recheck_reads_current_orders_after_an_earlier_repeatable_read_snapshot() throws Exception {
        var reporter=logged(account(CurrentUser.Role.REPORTER,null)); var admin=logged(account(CurrentUser.Role.ADMIN,null)); var worker=account(CurrentUser.Role.TECHNICIAN,CurrentUser.ReviewStatus.APPROVED); String request=create(reporter,locationA); long eq=device(locationA,"AIR_CONDITIONER",true);
        var snapshotEstablished=new CountDownLatch(1); var committed=new CountDownLatch(1);
        try(var pool=Executors.newSingleThreadExecutor()) {
            var rechecked=pool.submit(() -> new TransactionTemplate(transactions).execute(status -> {
                assertEquals("REPEATABLE-READ",db.queryForObject("SELECT @@transaction_isolation",String.class));
                assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM work_order WHERE equipment_id=?",Integer.class,eq)); snapshotEstablished.countDown();
                try { assertTrue(committed.await(15,TimeUnit.SECONDS)); } catch(InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException(failure); }
                deviceMapper.lock(eq); return deviceMapper.unfinished(eq);
            }));
            assertTrue(snapshotEstablished.await(15,TimeUnit.SECONDS));
            try { var response=approve(admin,request,eq,worker.id); assertEquals(200,response.statusCode(),response.body()); }
            finally { committed.countDown(); }
            assertEquals(1L,rechecked.get(20,TimeUnit.SECONDS));
        }
        var move=new LinkedHashMap<String,Object>(Map.of("operationId",UUID.randomUUID().toString(),"version",0,"name","具体审核设备","locationId",Long.toString(locationB),"addressText","合法台账地址","facilityType","AIR_CONDITIONER","reason","审核后不允许移动"));
        var response=admin.request("PATCH","/api/equipment/"+eq,move,admin.csrf); assertEquals(409,response.statusCode(),response.body()); assertEquals("EQUIPMENT_MOVE_BLOCKED",code(response)); assertEquals(locationA,db.queryForObject("SELECT location_id FROM equipment WHERE id=?",Long.class,eq));
    }

    @Test void simultaneous_identical_operations_both_return_the_original_single_order() throws Exception {
        var reporting=logged(account(CurrentUser.Role.REPORTER,null)); var manager=account(CurrentUser.Role.ADMIN,null); var adminA=logged(manager); var adminB=logged(manager); var worker=account(CurrentUser.Role.TECHNICIAN,CurrentUser.ReviewStatus.APPROVED);
        String request=create(reporting,locationA); long eq=device(locationA,"AIR_CONDITIONER",true); var dto=approve(eq,worker.id,0,UUID.randomUUID().toString()); var start=new CountDownLatch(1); String path="/api/repair-requests/"+request+"/approve-and-assign";
        try(var pool=Executors.newFixedThreadPool(2)) {
            var one=pool.submit(() -> { start.await(); return adminA.post(path,dto); }); var two=pool.submit(() -> { start.await(); return adminB.post(path,dto); }); start.countDown();
            var first=one.get(20,TimeUnit.SECONDS); var second=two.get(20,TimeUnit.SECONDS); assertEquals(200,first.statusCode(),first.body()); assertEquals(200,second.statusCode(),second.body()); assertEquals(data(first),data(second));
        }
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM work_order WHERE request_id=?",Integer.class,Long.parseLong(request))); assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM repair_request_event WHERE request_id=? AND event_type='APPROVED_ASSIGNED'",Integer.class,Long.parseLong(request)));
    }

    @Test void device_move_and_review_share_the_same_equipment_lock() throws Exception {
        var reporter=logged(account(CurrentUser.Role.REPORTER,null)); var reviewing=logged(account(CurrentUser.Role.ADMIN,null)); var maintaining=logged(account(CurrentUser.Role.ADMIN,null)); var worker=account(CurrentUser.Role.TECHNICIAN,CurrentUser.ReviewStatus.APPROVED);
        String request=create(reporter,locationA); long eq=device(locationA,"AIR_CONDITIONER",true); var move=Map.of("operationId",UUID.randomUUID().toString(),"version",0,"name","具体审核设备","locationId",Long.toString(locationB),"addressText","合法台账地址","facilityType","AIR_CONDITIONER","reason","明确移动"); var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var approved=pool.submit(() -> { start.await(); return approve(reviewing,request,eq,worker.id); }); var moved=pool.submit(() -> { start.await(); return maintaining.request("PATCH","/api/equipment/"+eq,move,maintaining.csrf); }); start.countDown();
            var reviewResponse=approved.get(20,TimeUnit.SECONDS); var moveResponse=moved.get(20,TimeUnit.SECONDS);
            if(reviewResponse.statusCode()==200) {
                assertEquals(409,moveResponse.statusCode(),moveResponse.body()); assertEquals("EQUIPMENT_MOVE_BLOCKED",code(moveResponse)); assertEquals(locationA,db.queryForObject("SELECT location_id FROM equipment WHERE id=?",Long.class,eq)); assertEquals(locationA,db.queryForObject("SELECT location_id FROM work_order WHERE request_id=?",Long.class,Long.parseLong(request)));
            } else {
                assertEquals(200,moveResponse.statusCode(),moveResponse.body()); assertEquals(409,reviewResponse.statusCode(),reviewResponse.body()); assertEquals("LOCATION_MISMATCH",code(reviewResponse)); assertEquals(locationB,db.queryForObject("SELECT location_id FROM equipment WHERE id=?",Long.class,eq)); pending(request);
            }
        }
    }

    @Test void no_qualified_people_returns_zero_and_never_approves_request() throws Exception {
        var reporter=logged(account(CurrentUser.Role.REPORTER,null)); var admin=logged(account(CurrentUser.Role.ADMIN,null)); var unapproved=account(CurrentUser.Role.TECHNICIAN,CurrentUser.ReviewStatus.PENDING); String request=create(reporter,locationA); long eq=device(locationA,"AIR_CONDITIONER",true);
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM user_account WHERE role='TECHNICIAN' AND enabled=1 AND review_status='APPROVED'",Integer.class),"This guarded scenario requires the otherwise-empty test fixture; do not delete unknown users");
        var response=admin.get("/api/admin/assignment-eligible?context=FIRST_ASSIGN&requestId="+request); assertEquals(200,response.statusCode(),response.body()); var eligible=data(response); assertEquals(0,eligible.get("eligibleTotal").asInt()); assertEquals(0,eligible.get("candidates").get("total").asInt()); assertTrue(eligible.get("candidates").get("records").isEmpty());
        var approval=approve(admin,request,eq,unapproved.id); assertEquals(409,approval.statusCode(),approval.body()); assertEquals("QUALIFICATION_CHANGED",code(approval)); pending(request);
    }

    @Test void duplicate_close_requires_concrete_legal_device_and_nonblank_reason_without_order() throws Exception {
        var reporter=logged(account(CurrentUser.Role.REPORTER,null)); var admin=logged(account(CurrentUser.Role.ADMIN,null)); String request=create(reporter,locationA); String path="/api/repair-requests/"+request+"/duplicate-close"; long eq=device(locationA,"AIR_CONDITIONER",false);
        assertEquals(400,admin.post(path,close(eq,"  ")).statusCode()); pending(request); assertEquals(404,admin.post(path,close(IdWorker.getId(),"必须匹配存在的具体台账")).statusCode()); pending(request);
        var outside=admin.post(path,close(device(locationA,"PROJECTOR",true),"不能把范围外当重复")); assertEquals(400,outside.statusCode(),outside.body()); assertEquals("OUT_OF_SCOPE_EQUIPMENT",code(outside)); pending(request);
        var mismatch=admin.post(path,close(device(locationB,"AIR_CONDITIONER",true),"不能选其他位置")); assertEquals(409,mismatch.statusCode(),mismatch.body()); pending(request);
        var dto=close(eq,"核实同一设备的重复描述，保留关闭依据"); var response=admin.post(path,dto); assertEquals(200,response.statusCode(),response.body()); assertEquals(data(response),data(admin.post(path,dto))); assertTrue(data(response).get("relatedResourceId").isNull()); assertTrue(data(response).get("resultRound").isNull());
        var detail=data(reporter.get("/api/repair-requests/"+request)); assertEquals("DUPLICATE_CLOSED",detail.get("status").asString()); assertTrue(detail.get("workOrder").isNull()); assertTrue(detail.get("allowedActions").isEmpty());
        var event=db.queryForMap("SELECT evidence_equipment_id,reason FROM repair_request_event WHERE request_id=? AND event_type='DUPLICATE_CLOSED'",Long.parseLong(request)); assertEquals(eq,((Number)event.get("evidence_equipment_id")).longValue()); assertEquals(dto.get("reason"),event.get("reason")); assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM work_order WHERE request_id=?",Integer.class,Long.parseLong(request)));
        var changed=new LinkedHashMap<>(dto); changed.put("reason","同一标识不同内容"); assertEquals("OPERATION_CONTENT_MISMATCH",code(admin.post(path,changed)));
    }

    @Test void eligible_returns_full_paginated_current_population_and_checks_context_and_roles() throws Exception {
        var reporter=logged(account(CurrentUser.Role.REPORTER,null)); var admin=logged(account(CurrentUser.Role.ADMIN,null)); String request=create(reporter,locationA); String tag="elig_"+UUID.randomUUID().toString().replace("-",""); var expected=new TreeSet<Long>();
        for(int i=0;i<7;i++) { var worker=account(CurrentUser.Role.TECHNICIAN,CurrentUser.ReviewStatus.APPROVED); db.update("UPDATE user_account SET name=? WHERE id=?",tag+i,worker.id); expected.add(worker.id); }
        var pendingWorker=account(CurrentUser.Role.TECHNICIAN,CurrentUser.ReviewStatus.PENDING); var rejected=account(CurrentUser.Role.TECHNICIAN,CurrentUser.ReviewStatus.REJECTED); var disabled=account(CurrentUser.Role.TECHNICIAN,CurrentUser.ReviewStatus.APPROVED);
        for(var invalid:List.of(pendingWorker,rejected,disabled)) db.update("UPDATE user_account SET name=? WHERE id=?",tag+"invalid",invalid.id); db.update("UPDATE user_account SET enabled=0 WHERE id=?",disabled.id);
        String path="/api/admin/assignment-eligible?context=FIRST_ASSIGN&requestId="+request; int global=db.queryForObject("SELECT COUNT(*) FROM user_account WHERE role='TECHNICIAN' AND enabled=1 AND review_status='APPROVED'",Integer.class); var seen=new TreeSet<Long>();
        for(int page=1;page<=4;page++) {
            var response=admin.get(path+"&keyword="+tag+"&page="+page+"&pageSize=2"); assertEquals(200,response.statusCode(),response.body()); var result=data(response); assertEquals(global,result.get("eligibleTotal").asInt()); assertEquals(7,result.get("candidates").get("total").asInt()); assertEquals(page,result.get("candidates").get("page").asInt());
            for(var candidate:result.get("candidates").get("records")) { seen.add(Long.parseLong(candidate.get("id").asString())); assertTrue(candidate.get("enabled").asBoolean()); assertTrue(candidate.get("eligible").asBoolean()); assertEquals("APPROVED",candidate.get("reviewStatus").asString()); assertTrue(candidate.get("refusal").isEmpty()); }
        }
        assertEquals(expected,seen); assertEquals(0,data(admin.get(path+"&keyword=does_not_exist_"+tag)).get("candidates").get("total").asInt());
        assertEquals(400,admin.get("/api/admin/assignment-eligible?context=FIRST_ASSIGN").statusCode()); assertEquals(400,admin.get(path+"&workOrderId="+request).statusCode()); assertEquals(404,admin.get("/api/admin/assignment-eligible?context=REASSIGN&workOrderId="+request).statusCode()); assertEquals(400,admin.get(path+"&pageSize=101").statusCode());
        assertEquals(403,reporter.get(path).statusCode()); var tech=logged(pendingWorker); assertEquals(403,tech.get(path).statusCode()); assertEquals(401,b04Browser().get(path).statusCode());
        db.update("UPDATE repair_request SET status='NEEDS_SUPPLEMENT',version=version+1 WHERE id=?",Long.parseLong(request)); assertEquals(409,admin.get(path).statusCode());
    }

    @Test void admin_list_count_and_mutation_permissions_use_actual_distinct_accounts() throws Exception {
        var a=logged(account(CurrentUser.Role.REPORTER,null)); var b=logged(account(CurrentUser.Role.REPORTER,null)); var admin=logged(account(CurrentUser.Role.ADMIN,null)); var worker=account(CurrentUser.Role.TECHNICIAN,CurrentUser.ReviewStatus.APPROVED); var tech=logged(worker);
        String requestA=create(a,locationA),requestB=create(b,locationA); long eq=device(locationA,"AIR_CONDITIONER",true); String query="/api/repair-requests?locationId="+locationA+"&status=PENDING_REVIEW&pageSize=1";
        var all=data(admin.get(query)); assertEquals(2,all.get("total").asInt()); assertEquals(1,all.get("records").size()); assertEquals(1,data(a.get(query)).get("total").asInt()); assertEquals(1,data(b.get(query)).get("total").asInt()); assertEquals(403,tech.get(query).statusCode()); assertEquals(401,b04Browser().get(query).statusCode());
        assertEquals(403,approve(a,requestA,eq,worker.id).statusCode()); assertEquals(403,approve(tech,requestA,eq,worker.id).statusCode()); assertEquals(403,a.post("/api/repair-requests/"+requestA+"/duplicate-close",close(eq,"无权限")).statusCode()); assertEquals(403,admin.request("POST","/api/repair-requests/"+requestA+"/approve-and-assign",approve(eq,worker.id,0,UUID.randomUUID().toString()),null).statusCode()); pending(requestA);
        assertEquals(200,admin.post("/api/repair-requests/"+requestB+"/return",Map.of("version",0,"operationId",UUID.randomUUID().toString(),"reason","原申请补充")).statusCode());
        assertEquals(1,data(admin.get(query)).get("total").asInt()); assertEquals(1,data(admin.get("/api/repair-requests?locationId="+locationA+"&status=NEEDS_SUPPLEMENT")).get("total").asInt()); assertEquals(0,data(a.get("/api/repair-requests?locationId="+locationA+"&status=NEEDS_SUPPLEMENT")).get("total").asInt());
    }

    @Test void admin_only_list_alias_rejects_real_reporter_cookie_after_account_switch() throws Exception {
        var reporting=account(CurrentUser.Role.REPORTER,null); var a=logged(reporting); var b=logged(account(CurrentUser.Role.REPORTER,null)); var admin=logged(account(CurrentUser.Role.ADMIN,null)); var tech=logged(account(CurrentUser.Role.TECHNICIAN,CurrentUser.ReviewStatus.APPROVED));
        String requestA=create(a,locationA),requestB=create(b,locationA); String query="/api/admin/repair-requests?locationId="+locationA+"&status=PENDING_REVIEW&page=1&pageSize=1";
        var response=admin.get(query); assertEquals(200,response.statusCode(),response.body()); var result=data(response); assertEquals(2,result.get("total").asInt()); assertEquals(1,result.get("records").size()); assertEquals(1,result.get("page").asInt()); assertEquals(1,result.get("pageSize").asInt());
        String requestNo=db.queryForObject("SELECT request_no FROM repair_request WHERE id=?",String.class,Long.parseLong(requestA)); assertEquals(1,data(admin.get(query+"&keyword="+requestNo)).get("total").asInt());
        assertEquals(403,a.get(query).statusCode()); assertEquals(403,tech.get(query).statusCode()); assertEquals(401,b04Browser().get(query).statusCode());
        assertEquals(200,admin.post("/api/repair-requests/"+requestB+"/return",Map.of("version",0,"operationId",UUID.randomUUID().toString(),"reason","原申请补充")).statusCode()); assertEquals(1,data(admin.get(query)).get("total").asInt()); assertEquals(1,data(admin.get("/api/admin/repair-requests?locationId="+locationA+"&status=NEEDS_SUPPLEMENT")).get("total").asInt());
        // This is a real second login in the same cookie jar, as with another browser tab.
        admin.login(reporting); assertEquals(1,data(admin.get("/api/repair-requests?locationId="+locationA+"&status=PENDING_REVIEW")).get("total").asInt()); var denied=admin.get(query); assertEquals(403,denied.statusCode(),denied.body()); assertEquals("FORBIDDEN",code(denied));
    }

    @Test void stale_version_and_non_pending_state_never_create_order() throws Exception {
        var reporter=logged(account(CurrentUser.Role.REPORTER,null)); var admin=logged(account(CurrentUser.Role.ADMIN,null)); var worker=account(CurrentUser.Role.TECHNICIAN,CurrentUser.ReviewStatus.APPROVED); String request=create(reporter,locationA); long eq=device(locationA,"AIR_CONDITIONER",true);
        var stale=admin.post("/api/repair-requests/"+request+"/approve-and-assign",approve(eq,worker.id,1,UUID.randomUUID().toString())); assertEquals(409,stale.statusCode(),stale.body()); assertEquals("VERSION_CONFLICT",code(stale)); pending(request);
        assertEquals(200,admin.post("/api/repair-requests/"+request+"/return",Map.of("version",0,"operationId",UUID.randomUUID().toString(),"reason","待补充")).statusCode());
        var state=admin.post("/api/repair-requests/"+request+"/approve-and-assign",approve(eq,worker.id,1,UUID.randomUUID().toString())); assertEquals(409,state.statusCode()); assertEquals("STATE_CONFLICT",code(state)); assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM work_order WHERE request_id=?",Integer.class,Long.parseLong(request)));
    }

    @Test void legal_projector_without_a_model_is_assignable() throws Exception {
        long classroom=location(floors.getFirst(),"class_"+IdWorker.getId(),"教室","CLASSROOM",true); var reporter=logged(account(CurrentUser.Role.REPORTER,null)); var admin=logged(account(CurrentUser.Role.ADMIN,null)); var worker=account(CurrentUser.Role.TECHNICIAN,CurrentUser.ReviewStatus.APPROVED); String request=create(reporter,classroom);
        var response=approve(admin,request,device(classroom,"PROJECTOR",true),worker.id); assertEquals(200,response.statusCode(),response.body()); assertEquals("PENDING_ACCEPT",data(admin.get("/api/work-orders/"+data(response).get("relatedResourceId").asString())).get("status").asString());
    }
}
