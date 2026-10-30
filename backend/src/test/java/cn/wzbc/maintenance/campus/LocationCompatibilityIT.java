package cn.wzbc.maintenance.campus;
import cn.wzbc.maintenance.security.CurrentUser;
import cn.wzbc.maintenance.support.*;
import cn.wzbc.maintenance.campus.service.LocationNameMaintenanceService;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import static org.junit.jupiter.api.Assertions.*;
class LocationCompatibilityIT extends B04HttpTestSupport {
    @Autowired LocationNameMaintenanceService maintenance;
    @AfterEach void cleanNameHistory(){delete("location_name_history","location_id",locations);}
    private String op(){return UUID.randomUUID().toString();}
    @Test void legacy_request_does_not_gain_location() throws Exception {
        var r=account(CurrentUser.Role.REPORTER,null);var a=account(CurrentUser.Role.ADMIN,null);var t=account(CurrentUser.Role.TECHNICIAN,CurrentUser.ReviewStatus.APPROVED);
        var owner=b04Browser();owner.login(r);var admin=browser();admin.login(a);var tech=b04Browser();tech.login(t);
        long request=Long.parseLong(data(owner.post("/api/repair-requests",createBody(upload(owner),locationA,op()))).get("resourceId").asString());
        db.update("UPDATE repair_request SET location_mode='LEGACY_TEXT',location_id=NULL,location_name_snapshot=NULL WHERE id=?",request);db.update("UPDATE repair_request_submission SET location_mode='LEGACY_TEXT',location_id=NULL,location_name_snapshot=NULL WHERE request_id=?",request);
        assertEquals(200,admin.post("/api/repair-requests/"+request+"/return",Map.of("version",0,"operationId",op(),"reason","补充原文字说明")).statusCode());
        var supplement=createBody("unused",locationA,op());supplement.remove("locationId");supplement.put("attachmentIds",List.of());supplement.put("version",1);assertEquals(200,owner.post("/api/repair-requests/"+request+"/supplement",supplement).statusCode());
        var fixture=new B04WorkOrderFixture(db,time.instant());long device=fixture.equipment(locationA);equipment.add(device);
        var approval=admin.post("/api/repair-requests/"+request+"/approve-and-assign",Map.of("version",2,"operationId",op(),"technicianId",t.id.toString(),"equipmentId",Long.toString(device),"confirmPreviouslyRefused",false));assertEquals(200,approval.statusCode());String order=data(approval).get("relatedResourceId").asString();
        Long approvedLegacyWorkOrderLocationId=db.queryForObject("SELECT location_id FROM work_order WHERE id=?",Long.class,Long.parseLong(order));assertNull(approvedLegacyWorkOrderLocationId);
        assertEquals(200,tech.post("/api/work-orders/"+order+"/accept",Map.of("version",0,"operationId",op())).statusCode());
        var image=tech.upload(Map.of("operationId",op(),"purpose","REPAIR_VOUCHER","workOrderId",order,"round","1"),"voucher.png","image/png",png(),true);assertEquals(201,image.statusCode());
        assertEquals(200,tech.post("/api/work-orders/"+order+"/submit-result",Map.of("version",1,"round",1,"operationId",op(),"result","旧文字维修完成","attachmentIds",List.of(data(image).get("id").asString()))).statusCode());
        assertEquals(200,owner.post("/api/work-orders/"+order+"/acceptance",Map.of("version",2,"round",1,"operationId",op(),"passed",true)).statusCode());
        assertNull(db.queryForObject("SELECT location_id FROM work_order WHERE id=?",Long.class,Long.parseLong(order)));
    }
    @Test void name_correction_retains_id_history_and_original_request_snapshot() throws Exception {
        var r=account(CurrentUser.Role.REPORTER,null);var b=b04Browser();b.login(r);
        String first=data(b.post("/api/repair-requests",createBody(upload(b),locationA,op()))).get("resourceId").asString();String old=db.queryForObject("SELECT location_name_snapshot FROM repair_request WHERE id=?",String.class,Long.parseLong(first));
        var result=maintenance.correct(locationA,0,"101修正","合成本地维护者","标注名称修正");assertEquals(locationA,result.locationId());assertEquals(1,result.version());
        assertEquals(old,db.queryForObject("SELECT location_name_snapshot FROM repair_request WHERE id=?",String.class,Long.parseLong(first)));
        String second=data(b.post("/api/repair-requests",createBody(upload(b),locationA,op()))).get("resourceId").asString();assertTrue(db.queryForObject("SELECT location_name_snapshot FROM repair_request WHERE id=?",String.class,Long.parseLong(second)).endsWith("101修正"));
        assertEquals("101",db.queryForObject("SELECT old_name FROM location_name_history WHERE location_id=?",String.class,locationA));assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM location_name_history WHERE location_id=?",Integer.class,locationA));
    }
    @Test void approval_and_device_move_only_commit_consistent_results() throws Exception {
        var r=account(CurrentUser.Role.REPORTER,null);var a=account(CurrentUser.Role.ADMIN,null);var other=account(CurrentUser.Role.ADMIN,null);var t=account(CurrentUser.Role.TECHNICIAN,CurrentUser.ReviewStatus.APPROVED);
        var owner=b04Browser();owner.login(r);var admin=browser();admin.login(a);var mover=browser();mover.login(other);long request=Long.parseLong(data(owner.post("/api/repair-requests",createBody(upload(owner),locationA,op()))).get("resourceId").asString());var f=new B04WorkOrderFixture(db,time.instant());long device=f.equipment(locationA);equipment.add(device);
        var latch=new CountDownLatch(1);try(var pool=Executors.newFixedThreadPool(2)){
            var approve=pool.submit(()->{latch.await();return admin.post("/api/repair-requests/"+request+"/approve-and-assign",Map.of("version",0,"operationId",op(),"technicianId",t.id.toString(),"equipmentId",Long.toString(device),"confirmPreviouslyRefused",false)).statusCode();});
            var move=pool.submit(()->{latch.await();return mover.request("PATCH","/api/equipment/"+device,Map.of("version",0,"operationId",op(),"name","演示空调","locationId",Long.toString(locationB),"addressText","新地址","facilityType","AIR_CONDITIONER","reason","测试移动"),mover.csrf).statusCode();});latch.countDown();int approved=approve.get(15,TimeUnit.SECONDS),moved=move.get(15,TimeUnit.SECONDS);
            assertTrue(approved==200&&moved==409||approved==409&&moved==200,"one consistent winner");assertEquals(approved==200?locationA:locationB,db.queryForObject("SELECT location_id FROM equipment WHERE id=?",Long.class,device));
            if(approved==200)assertEquals(locationA,db.queryForObject("SELECT location_id FROM work_order WHERE request_id=?",Long.class,request));
        }
    }
}
