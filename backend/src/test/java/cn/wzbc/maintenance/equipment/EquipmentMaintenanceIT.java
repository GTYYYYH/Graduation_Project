package cn.wzbc.maintenance.equipment;
import cn.wzbc.maintenance.Application;
import cn.wzbc.maintenance.account.entity.UserEntity;
import cn.wzbc.maintenance.support.*;
import cn.wzbc.maintenance.security.CurrentUser;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(classes={Application.class,B02HttpTestSupport.TimeConfig.class},webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class EquipmentMaintenanceIT extends B04HttpTestSupport {
    private Map<String,Object> save(long location,Integer version,String operation) {
        var body=new LinkedHashMap<String,Object>(Map.of("operationId",operation,"name","合法同名空调","locationId",Long.toString(location),"addressText","台账原文","facilityType","AIR_CONDITIONER")); if(version!=null) body.put("version",version); return body;
    }
    private long create(B04Browser b) throws Exception { var r=b.post("/api/equipment",save(locationA,null,UUID.randomUUID().toString())); assertEquals(201,r.statusCode()); return Long.parseLong(data(r).get("resourceId").asString()); }
    @Test void unfinished_device_cannot_move() throws Exception {
        UserEntity admin=account(CurrentUser.Role.ADMIN,null),reporter=account(CurrentUser.Role.REPORTER,null),worker=account(CurrentUser.Role.TECHNICIAN,CurrentUser.ReviewStatus.APPROVED);
        var manager=b04Browser(); manager.login(admin); long eq=create(manager); var b=b04Browser(); b.login(reporter); var created=b.post("/api/repair-requests",createBody(upload(b),locationA,UUID.randomUUID().toString())); assertEquals(201,created.statusCode()); long request=Long.parseLong(data(created).get("resourceId").asString());
        long order=new B04WorkOrderFixture(db,time.instant()).order(request,eq,worker.id,admin.id,"PENDING_ACCEPT"); var body=save(locationB,0,UUID.randomUUID().toString()); body.put("reason","显式移动原因");
        var moveUnfinishedDeviceResponse=manager.request("PATCH","/api/equipment/"+eq,body,manager.csrf); assertEquals(409,moveUnfinishedDeviceResponse.statusCode());
        assertEquals(locationA,db.queryForObject("SELECT location_id FROM equipment WHERE id=?",Long.class,eq)); assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM equipment_location_history WHERE equipment_id=?",Integer.class,eq));
        assertEquals(200,manager.post("/api/equipment/"+eq+"/disable",Map.of("version",0,"operationId",UUID.randomUUID().toString())).statusCode());
        var edited=save(locationA,1,UUID.randomUUID().toString()); edited.put("name","更新台账名称"); assertEquals(200,manager.request("PATCH","/api/equipment/"+eq,edited,manager.csrf).statusCode());
        assertFalse(db.queryForObject("SELECT enabled FROM equipment WHERE id=?",Boolean.class,eq)); assertEquals("PENDING_ACCEPT",db.queryForObject("SELECT status FROM work_order WHERE id=?",String.class,order));
        assertEquals(locationA,db.queryForObject("SELECT location_id FROM work_order WHERE id=?",Long.class,order)); assertEquals("演示空调",db.queryForObject("SELECT equipment_name FROM repair_request WHERE id=?",String.class,request));
    }
    @Test void allowed_duplicate_names_and_server_number_preserve_operation_result() throws Exception {
        var b=b04Browser(); b.login(account(CurrentUser.Role.ADMIN,null)); String op=UUID.randomUUID().toString(); var body=save(locationA,null,op); var first=b.post("/api/equipment",body); assertEquals(201,first.statusCode()); String id=data(first).get("resourceId").asString();
        assertEquals(id,data(b.post("/api/equipment",body)).get("resourceId").asString()); long second=create(b); assertNotEquals(id,Long.toString(second)); assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM equipment WHERE location_id=?",Integer.class,locationA));
        var detail=data(b.get("/api/equipment/"+id)); assertEquals("EQ-"+id,detail.get("equipment").get("equipmentNo").asString()); assertTrue(detail.get("history").get("records").isEmpty()); assertTrue(detail.get("equipment").get("currentWorkOrder").isNull());
        var altered=new LinkedHashMap<>(body); altered.put("name","不同内容"); assertEquals(409,b.post("/api/equipment",altered).statusCode()); assertEquals("SUCCEEDED",data(b.get("/api/operations/"+op+"?operation=EQUIPMENT_CREATE")).get("kind").asString());
    }
    @Test void scope_and_untrusted_number_or_enabled_are_rejected() throws Exception {
        var b=b04Browser(); b.login(account(CurrentUser.Role.ADMIN,null)); var body=save(observation,null,UUID.randomUUID().toString()); assertEquals(400,b.post("/api/equipment",body).statusCode());
        body=save(locationA,null,UUID.randomUUID().toString()); body.put("facilityType","PROJECTOR"); assertEquals(400,b.post("/api/equipment",body).statusCode());
        body=save(locationA,null,UUID.randomUUID().toString()); body.put("equipmentNo","FORGED"); assertEquals(400,b.post("/api/equipment",body).statusCode());
        body=save(locationA,null,UUID.randomUUID().toString()); body.put("enabled",false); assertEquals(400,b.post("/api/equipment",body).statusCode()); assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM equipment WHERE location_id=?",Integer.class,locationA));
    }
    @Test void explicit_move_and_legacy_location_keep_old_address_and_snapshot() throws Exception {
        var admin=account(CurrentUser.Role.ADMIN,null); var b=b04Browser(); b.login(admin); long eq=create(b); var body=save(locationB,0,UUID.randomUUID().toString());
        assertEquals(400,b.request("PATCH","/api/equipment/"+eq,body,b.csrf).statusCode()); body.put("reason","搬到102，保留原台账"); assertEquals(200,b.request("PATCH","/api/equipment/"+eq,body,b.csrf).statusCode());
        assertEquals(locationA,db.queryForObject("SELECT old_location_id FROM equipment_location_history WHERE equipment_id=?",Long.class,eq)); assertEquals(locationB,db.queryForObject("SELECT new_location_id FROM equipment_location_history WHERE equipment_id=?",Long.class,eq));
        long legacy=IdWorker.getId(); equipment.add(legacy); db.update("INSERT INTO equipment(id,created_at,equipment_no,name,address_text,facility_type,enabled,version,updated_at) VALUES(?,?,?,'旧文字空调','原始自由地址，不解析','AIR_CONDITIONER',0,0,?)",legacy,time.instant(),"EQ-"+legacy,time.instant());
        var explicit=save(locationA,0,UUID.randomUUID().toString()); explicit.put("reason","管理员明确选择位置"); assertEquals(200,b.request("PATCH","/api/equipment/"+legacy,explicit,b.csrf).statusCode());
        assertEquals("原始自由地址，不解析",db.queryForObject("SELECT old_address_text FROM equipment_location_history WHERE equipment_id=?",String.class,legacy)); assertFalse(db.queryForObject("SELECT enabled FROM equipment WHERE id=?",Boolean.class,legacy));
    }
    @Test void version_roles_and_current_history_are_real() throws Exception {
        UserEntity admin=account(CurrentUser.Role.ADMIN,null),reporter=account(CurrentUser.Role.REPORTER,null),worker=account(CurrentUser.Role.TECHNICIAN,CurrentUser.ReviewStatus.APPROVED); var manager=b04Browser(); manager.login(admin); long eq=create(manager);
        var b=b04Browser(); b.login(reporter); var created=b.post("/api/repair-requests",createBody(upload(b),locationA,UUID.randomUUID().toString())); long req=Long.parseLong(data(created).get("resourceId").asString()); long order=new B04WorkOrderFixture(db,time.instant()).order(req,eq,worker.id,admin.id,"COMPLETED");
        var detail=data(manager.get("/api/equipment/"+eq)); assertTrue(detail.get("equipment").get("currentWorkOrder").isNull()); assertEquals(Long.toString(order),detail.get("history").get("records").get(0).get("id").asString());
        assertEquals(409,manager.post("/api/equipment/"+eq+"/disable",Map.of("version",1,"operationId",UUID.randomUUID().toString())).statusCode()); assertTrue(db.queryForObject("SELECT enabled FROM equipment WHERE id=?",Boolean.class,eq));
        assertEquals(403,b.get("/api/equipment").statusCode()); assertEquals(403,b.get("/api/equipment/"+eq).statusCode()); assertEquals(403,b.post("/api/equipment",save(locationA,null,UUID.randomUUID().toString())).statusCode());
        assertEquals(1,data(manager.get("/api/equipment?locationId="+locationA+"&enabled=true")).get("total").asInt()); assertEquals(0,data(manager.get("/api/equipment?locationId="+locationB)).get("total").asInt());
    }
}
