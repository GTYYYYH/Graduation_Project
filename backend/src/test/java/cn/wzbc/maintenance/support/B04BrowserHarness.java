package cn.wzbc.maintenance.support;

import cn.wzbc.maintenance.Application;
import cn.wzbc.maintenance.campus.service.CampusInitializationService;
import cn.wzbc.maintenance.security.CurrentUser;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import static org.junit.jupiter.api.Assertions.*;

/** Explicit, test-only real browser fixture. It is excluded from normal *Test/*IT runs. */
@SpringBootTest(classes={Application.class,B02HttpTestSupport.TimeConfig.class},webEnvironment=SpringBootTest.WebEnvironment.DEFINED_PORT,
    properties={"server.port=8081","campus.browser-harness=true"})
class B04BrowserHarness extends B04HttpTestSupport {
    @Autowired CampusInitializationService campus;
    private Path lastControl;
    @Test void failure_cleanup_after_control_creation() throws Exception {
        assertThrows(IllegalStateException.class,() -> fixture(true));
        assertFalse(Files.exists(lastControl));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM user_account",Integer.class));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM campus_building",Integer.class));
    }
    @Test void isolated_browser_fixture_lifecycle() throws Exception { fixture(false); }
    @Test void recover_interrupted_fixture() throws Exception {
        Path project=Path.of("..").toAbsolutePath().normalize(); var saved=json.readTree(Files.readString(project.resolve("tests/evidence/B04-browser-control.json")));
        assertEquals("test",saved.get("profile").asString()); assertEquals("campus_maintenance_test",saved.get("database").asString());
        var recorded=new ArrayList<Long>(); for(var value:saved.get("ownedAccountIds")) { long id=value.asLong(); assertTrue(id>0); recorded.add(id); }
        var aliases=new HashSet<Long>(); for(var value:saved.get("accounts").properties()) aliases.add(Long.parseLong(value.getValue().asString())); assertEquals(new HashSet<>(recorded),aliases);
        for(long id:recorded) { var user=users.selectById(id); if(user!=null) assertTrue(user.loginName.startsWith("b04_ui_"),"Refuse cleanup of an unrecognized account"); }
        Path control=Path.of(saved.get("controlDirectory").asString()).toAbsolutePath().normalize(); assertEquals(attachmentRoot,control.getParent()); assertTrue(control.getFileName().toString().matches("b04-browser-[0-9a-f-]{36}"));
        if(Files.exists(control,LinkOption.NOFOLLOW_LINKS)) { assertTrue(Files.isDirectory(control,LinkOption.NOFOLLOW_LINKS)); assertEquals(attachmentRoot,control.toRealPath().getParent()); }
        owned.addAll(recorded); var campusIds=saved.get("ownedCampusIds");
        for(var v:campusIds.get("buildings")) buildings.add(v.asLong()); for(var v:campusIds.get("floors")) floors.add(v.asLong()); for(var v:campusIds.get("locations")) locations.add(v.asLong()); for(var v:campusIds.get("equipment")) equipment.add(v.asLong());
        b04Cleanup(); cleanupOwnedOnly();
        for(var v:campusIds.get("initialization")) db.update("DELETE FROM campus_initialization WHERE id=?",v.asLong());
        if(Files.exists(control,LinkOption.NOFOLLOW_LINKS)) { for(String name:List.of("ready","done","B04-fault-demo.png")) { var path=control.resolve(name); if(Files.exists(path,LinkOption.NOFOLLOW_LINKS)) { assertTrue(Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)); assertEquals(control.toRealPath(),path.toRealPath().getParent()); Files.delete(path); } } Files.delete(control); }
        assertFalse(Files.exists(control));
        for(long id:recorded) assertNull(users.selectById(id));
    }
    private void fixture(boolean failAfterControl) throws Exception {
        Path root=Path.of("..").toAbsolutePath().normalize(), control=null;
        var snapshots=new ArrayList<Long>();
        try {
            // Preflight is inside try/finally. Unknown existing data is never swept.
            for(String table:List.of("user_account","campus_building","campus_floor","campus_location","equipment","campus_initialization","attachment_file","repair_request","work_order")) assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM "+table,Integer.class),"Browser fixture requires a clean, explicitly verified test database");
            control=Files.createDirectory(attachmentRoot.resolve("b04-browser-"+UUID.randomUUID())); lastControl=control;
            assertEquals(attachmentRoot,control.toRealPath().getParent());
            if(failAfterControl) throw new IllegalStateException("expected fixture preflight failure");
            time.set(Instant.now().truncatedTo(ChronoUnit.MILLIS));
            assertTrue(campus.initialize(root.resolve("config/campus.json")));
            buildings.addAll(db.queryForList("SELECT id FROM campus_building",Long.class)); floors.addAll(db.queryForList("SELECT id FROM campus_floor",Long.class)); locations.addAll(db.queryForList("SELECT id FROM campus_location",Long.class)); equipment.addAll(db.queryForList("SELECT id FROM equipment",Long.class)); snapshots.addAll(db.queryForList("SELECT id FROM campus_initialization",Long.class));
            var aliases=new LinkedHashMap<String,String>();
            for(var entry:List.of(new String[]{"b04_ui_admin","ADMIN","","13909991001","B04演示管理员"},new String[]{"b04_ui_reporter","REPORTER","","13909991002","B04演示报修人A"},new String[]{"b04_ui_other","REPORTER","","13909991003","B04演示报修人B"},new String[]{"b04_ui_tech","TECHNICIAN","APPROVED","13909991004","B04演示维修人员"},new String[]{"b04_ui_pending","TECHNICIAN","PENDING","13909991005","B04演示待审维修"})) {
                var role=CurrentUser.Role.valueOf(entry[1]); var review=entry[2].isEmpty()?null:CurrentUser.ReviewStatus.valueOf(entry[2]);
                cn.wzbc.maintenance.account.entity.UserEntity u;
                if(review==CurrentUser.ReviewStatus.PENDING) {
                    // A real pending registration needs its immutable submitted snapshot.
                    var registration=b04Browser(); registration.csrf();
                    var response=registration.post("/api/auth/register",Map.of("role",role.name(),"loginName",entry[0],"name",entry[4],"phone",entry[3],"password",PASSWORD,"confirmPassword",PASSWORD));
                    assertEquals(201,response.statusCode()); long registered=Long.parseLong(data(response).get("id").asString()); owned.add(registered); u=users.selectById(registered);
                } else u=account(role,review);
                db.update("UPDATE user_account SET login_name=?,phone=?,name=? WHERE id=?",entry[0],entry[3],entry[4],u.id); aliases.put(entry[0],u.id.toString());
            }
            Path image=control.resolve("B04-fault-demo.png"); var bitmap=new BufferedImage(320,240,BufferedImage.TYPE_INT_RGB); var graphics=bitmap.createGraphics();
            graphics.setColor(new java.awt.Color(234,243,246)); graphics.fillRect(0,0,320,240); graphics.setColor(new java.awt.Color(21,94,117)); graphics.fillRect(45,45,230,140); graphics.setColor(java.awt.Color.WHITE); graphics.drawString("B04 synthetic fault image",70,115); graphics.dispose(); assertTrue(ImageIO.write(bitmap,"png",image.toFile()));
            var metadata=new LinkedHashMap<String,Object>(); metadata.put("profile","test"); metadata.put("database","campus_maintenance_test"); metadata.put("backendPort",port); metadata.put("controlDirectory",control.toString()); metadata.put("imageFile",image.toString()); metadata.put("accounts",aliases);
            if(environment.getProperty("campus.browser-history",Boolean.class,false)) {
                cn.wzbc.maintenance.account.entity.UserEntity admin=users.selectById(Long.valueOf(aliases.get("b04_ui_admin"))),reporter=users.selectById(Long.valueOf(aliases.get("b04_ui_reporter"))),worker=users.selectById(Long.valueOf(aliases.get("b04_ui_tech")));
                var former=account(CurrentUser.Role.TECHNICIAN,CurrentUser.ReviewStatus.APPROVED); db.update("UPDATE user_account SET login_name='b04_ui_former',name='B04演示曾拒单者' WHERE id=?",former.id); aliases.put("b04_ui_former",former.id.toString());
                var b=b04Browser(); b.login(reporter); long loc=db.queryForObject("SELECT id FROM campus_location WHERE code='wb1-f1-r101'",Long.class);
                var body=createBody(upload(b),loc,UUID.randomUUID().toString()); body.put("equipmentName","B04隔离历史故障"); var response=b.post("/api/repair-requests",body); assertEquals(201,response.statusCode()); long request=Long.parseLong(data(response).get("resourceId").asString());
                long eq=db.queryForObject("SELECT id FROM equipment WHERE location_id=? AND facility_type='AIR_CONDITIONER'",Long.class,loc); var fixture=new B04WorkOrderFixture(db,time.instant()); long order=fixture.order(request,eq,worker.id,admin.id,"COMPLETED"); long submission=db.queryForObject("SELECT current_submission_id FROM repair_request WHERE id=?",Long.class,request); fixture.formerBeforeCurrent(order,former.id,admin.id,submission);
                metadata.put("historyFixture",Map.of("source","explicit test-only SQL; no B05 endpoint","requestId",Long.toString(request),"equipmentId",Long.toString(eq),"completedOrderId",Long.toString(order))); time.set(time.instant().plusSeconds(20));
            }
            metadata.put("clock",time.instant().toString()); metadata.put("ownedAccountIds",List.copyOf(owned)); metadata.put("ownedCampusIds",Map.of("buildings",List.copyOf(buildings),"floors",List.copyOf(floors),"locations",List.copyOf(locations),"equipment",List.copyOf(equipment),"initialization",snapshots));
            Files.writeString(root.resolve("tests/evidence/B04-browser-control.json"),json.writeValueAsString(metadata)); Files.writeString(control.resolve("ready"),"ready",StandardOpenOption.CREATE_NEW);
            long deadline=System.nanoTime()+Duration.ofHours(4).toNanos();
            while(!Files.exists(control.resolve("done")) && System.nanoTime()<deadline) Thread.sleep(250);
            assertTrue(Files.exists(control.resolve("done")),"Browser fixture timed out; exact cleanup still runs");
        } finally {
            // Base cleanup handles only registered uploader/reporter IDs and recorded campus IDs.
            b04Cleanup(); cleanupOwnedOnly();
            for(long id:snapshots) assertEquals(1,db.update("DELETE FROM campus_initialization WHERE id=?",id));
            if(control!=null && Files.exists(control,LinkOption.NOFOLLOW_LINKS)) {
                assertEquals(attachmentRoot,control.toRealPath().getParent());
                for(String name:List.of("ready","done","B04-fault-demo.png")) { Path p=control.resolve(name); if(Files.exists(p,LinkOption.NOFOLLOW_LINKS)) { assertTrue(Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)); assertEquals(control.toRealPath(),p.toRealPath().getParent()); Files.delete(p); } }
                Files.delete(control);
            }
        }
    }
}
