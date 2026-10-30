package cn.wzbc.maintenance.workorder;

import cn.wzbc.maintenance.Application;
import cn.wzbc.maintenance.account.entity.UserEntity;
import cn.wzbc.maintenance.support.*;
import cn.wzbc.maintenance.security.CurrentUser;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes={Application.class,B02HttpTestSupport.TimeConfig.class},webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class WorkOrderQueryIT extends B04HttpTestSupport {
    private long request(B04Browser b) throws Exception { var r=b.post("/api/repair-requests",createBody(upload(b),locationA,UUID.randomUUID().toString())); assertEquals(201,r.statusCode()); return Long.parseLong(data(r).get("resourceId").asString()); }
    private B04WorkOrderFixture fixture() { return new B04WorkOrderFixture(db,time.instant()); }
    private long voucher(long order,long technician) throws Exception {
        long round=db.queryForObject("SELECT id FROM repair_round WHERE work_order_id=? AND round_no=1",Long.class,order), id=IdWorker.getId(); byte[] bytes=png(); String key=UUID.randomUUID()+".png"; Files.write(attachmentRoot.resolve(key),bytes);
        db.update("INSERT INTO attachment_file(id,created_at,uploader_id,upload_operation_id,purpose,state,target_work_order_id,target_round,original_name,storage_key,media_type,byte_size,file_digest,uploaded_at,expires_at,bound_round_id,bound_at) VALUES(?,?,?,?,'REPAIR_VOUCHER','BOUND',?,1,'later.png',?,'image/png',?,?,?,?,?,?)",id,time.instant(),technician,UUID.randomUUID().toString(),order,key,bytes.length,MessageDigest.getInstance("SHA-256").digest(bytes),time.instant(),time.instant().plusSeconds(86400),round,time.instant()); return id;
    }
    @Test void former_assignee_cannot_read_later_voucher() throws Exception {
        UserEntity reporter=account(CurrentUser.Role.REPORTER,null),admin=account(CurrentUser.Role.ADMIN,null),former=account(CurrentUser.Role.TECHNICIAN,CurrentUser.ReviewStatus.APPROVED),worker=account(CurrentUser.Role.TECHNICIAN,CurrentUser.ReviewStatus.APPROVED);
        var b=b04Browser(); b.login(reporter); long req=request(b),eq=fixture().equipment(locationA); equipment.add(eq); long order=fixture().order(req,eq,worker.id,admin.id,"IN_PROGRESS");
        long submission=db.queryForObject("SELECT current_submission_id FROM repair_request WHERE id=?",Long.class,req); fixture().formerBeforeCurrent(order,former.id,admin.id,submission); String later=Long.toString(voucher(order,worker.id));
        var old=b04Browser(); old.login(former); var detail=old.get("/api/work-orders/"+order); assertEquals(200,detail.statusCode()); var projected=data(detail);
        assertEquals("ASSIGNMENT_ONLY",projected.get("historyScope").asString()); assertTrue(projected.get("rounds").isEmpty()); assertTrue(projected.get("version").isNull()); assertTrue(projected.get("assignedTechnician").isNull()); assertTrue(projected.get("acceptedAt").isNull());
        assertEquals(1,projected.get("assignments").size()); assertEquals(former.id.toString(),projected.get("assignments").get(0).get("technician").get("id").asString());
        String original=projected.get("requestMaterial").get("attachments").get(0).get("id").asString(); assertEquals(200,old.content(original).statusCode());
        var laterVoucherResponse=old.content(later); assertEquals(403,laterVoucherResponse.statusCode());
        var actual=b04Browser(); actual.login(worker); assertEquals(200,actual.content(later).statusCode()); assertEquals("FULL",data(actual.get("/api/work-orders/"+order)).get("historyScope").asString());
        assertEquals(0,data(old.get("/api/work-orders?scope=CURRENT")).get("total").asInt()); assertEquals(1,data(old.get("/api/work-orders?scope=HISTORY")).get("total").asInt());
    }
    @Test void completed_actual_worker_keeps_full_material_and_pages_do_not_expand_by_images() throws Exception {
        UserEntity reporter=account(CurrentUser.Role.REPORTER,null),admin=account(CurrentUser.Role.ADMIN,null),worker=account(CurrentUser.Role.TECHNICIAN,CurrentUser.ReviewStatus.APPROVED); var b=b04Browser(); b.login(reporter);
        long req=request(b),eq=fixture().equipment(locationA); equipment.add(eq); long order=fixture().order(req,eq,worker.id,admin.id,"COMPLETED"); String image=Long.toString(voucher(order,worker.id)); voucher(order,worker.id);
        var actual=b04Browser(); actual.login(worker); var detail=data(actual.get("/api/work-orders/"+order)); assertEquals("FULL",detail.get("historyScope").asString()); assertEquals(2,detail.get("rounds").get(0).get("attachments").size()); assertEquals(200,actual.content(image).statusCode());
        var page=data(actual.get("/api/work-orders?scope=HISTORY&page=1&pageSize=1")); assertEquals(1,page.get("total").asInt()); assertEquals(1,page.get("records").size());
        assertEquals(0,data(actual.get("/api/work-orders?scope=CURRENT")).get("total").asInt());
    }
    @Test void permissions_filter_before_total_and_cannot_select_someone_else_as_self() throws Exception {
        UserEntity reporter=account(CurrentUser.Role.REPORTER,null),admin=account(CurrentUser.Role.ADMIN,null),worker=account(CurrentUser.Role.TECHNICIAN,CurrentUser.ReviewStatus.APPROVED),other=account(CurrentUser.Role.REPORTER,null); var b=b04Browser(); b.login(reporter);
        long req=request(b),eq=fixture().equipment(locationA); equipment.add(eq); long order=fixture().order(req,eq,worker.id,admin.id,"PENDING_ACCEPT");
        var foreign=b04Browser(); foreign.login(other); assertEquals(403,foreign.get("/api/work-orders/"+order).statusCode()); assertEquals(0,data(foreign.get("/api/work-orders")).get("total").asInt());
        var technician=b04Browser(); technician.login(worker); assertEquals(403,technician.get("/api/work-orders?technicianId="+admin.id).statusCode()); assertEquals(1,data(technician.get("/api/work-orders")).get("total").asInt());
        var manager=b04Browser(); manager.login(admin); assertEquals(1,data(manager.get("/api/work-orders")).get("total").asInt()); assertEquals(400,manager.get("/api/work-orders?scope=unknown").statusCode());
        assertEquals(200,technician.post("/api/work-orders/"+order+"/accept",Map.of("operationId",UUID.randomUUID().toString(),"version",0)).statusCode());
    }
    @Test void real_unique_keys_block_two_unfinished_orders_and_duplicate_request() throws Exception {
        UserEntity reporter=account(CurrentUser.Role.REPORTER,null),admin=account(CurrentUser.Role.ADMIN,null),worker=account(CurrentUser.Role.TECHNICIAN,CurrentUser.ReviewStatus.APPROVED); var b=b04Browser(); b.login(reporter);
        long req=request(b),second=request(b),eq=fixture().equipment(locationA); equipment.add(eq); fixture().order(req,eq,worker.id,admin.id,"PENDING_ACCEPT");
        assertThrows(DataIntegrityViolationException.class,() -> fixture().order(second,eq,worker.id,admin.id,"PENDING_ACCEPT"));
        long anotherEq=fixture().equipment(locationA); equipment.add(anotherEq); assertThrows(DataIntegrityViolationException.class,() -> fixture().order(req,anotherEq,worker.id,admin.id,"PENDING_ACCEPT"));
    }
    @Test void composite_foreign_keys_reject_other_orders_assignment_and_round() throws Exception {
        UserEntity reporter=account(CurrentUser.Role.REPORTER,null),admin=account(CurrentUser.Role.ADMIN,null),worker=account(CurrentUser.Role.TECHNICIAN,CurrentUser.ReviewStatus.APPROVED); var b=b04Browser(); b.login(reporter);
        long req=request(b),second=request(b),eq=fixture().equipment(locationA),eq2=fixture().equipment(locationA); equipment.addAll(List.of(eq,eq2)); long order=fixture().order(req,eq,worker.id,admin.id,"IN_PROGRESS"),order2=fixture().order(second,eq2,worker.id,admin.id,"IN_PROGRESS");
        long assignment=db.queryForObject("SELECT current_assignment_id FROM work_order WHERE id=?",Long.class,order2),round=db.queryForObject("SELECT id FROM repair_round WHERE work_order_id=?",Long.class,order2);
        assertThrows(DataIntegrityViolationException.class,() -> db.update("UPDATE work_order SET current_assignment_id=? WHERE id=?",assignment,order));
        assertThrows(DataIntegrityViolationException.class,() -> db.update("INSERT INTO repair_progress(id,created_at,work_order_id,round_id,technician_id,content,recorded_at) VALUES(?,?,?,?,?,'跨单进度',?)",IdWorker.getId(),time.instant(),order,round,worker.id,time.instant()));
    }
}
