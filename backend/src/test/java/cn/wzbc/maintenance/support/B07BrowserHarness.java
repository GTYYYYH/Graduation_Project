package cn.wzbc.maintenance.support;

import cn.wzbc.maintenance.Application;
import cn.wzbc.maintenance.account.entity.UserEntity;
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

/** Explicit B07-only browser fixture. No HTTP test endpoints; excluded from default verify. */
@SpringBootTest(classes={Application.class,B02HttpTestSupport.TimeConfig.class},webEnvironment=SpringBootTest.WebEnvironment.DEFINED_PORT,
    properties={"server.port=8081","campus.browser-harness=true"})
class B07BrowserHarness extends B04HttpTestSupport {
    @Autowired CampusInitializationService campus;
    private Path lastControl;
    private Path evidence(Path project,String suffix) {
        String prefix=environment.getProperty("campus.browser-evidence-prefix","B07");
        assertTrue(prefix.matches("B07(?:-[a-z0-9]+)*"));
        return project.resolve("tests/evidence/"+prefix+"-"+suffix+".json");
    }
    @Test void ready_failure_cleans_owned_accounts_campus_and_files() throws Exception {
        assertThrows(IllegalStateException.class,() -> fixture(true));
        assertFalse(Files.exists(lastControl));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM user_account",Integer.class));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM campus_initialization",Integer.class));
    }
    @Test void isolated_browser_fixture_lifecycle() throws Exception { fixture(false); }
    @Test void resume_registered_fixture_for_browser() throws Exception {
        Path project=Path.of("..").toAbsolutePath().normalize(); var saved=json.readTree(Files.readString(evidence(project,"browser-control")));
        assertEquals("test",saved.get("profile").asString()); assertEquals("campus_maintenance_test",saved.get("database").asString());
        Path control=Path.of(saved.get("controlDirectory").asString()).toAbsolutePath().normalize();
        assertEquals(attachmentRoot,control.getParent()); assertTrue(control.getFileName().toString().matches("b07-browser-[0-9a-f-]{36}"));
        assertTrue(Files.exists(control.resolve("ready"))); assertFalse(Files.exists(control.resolve("done")));
        for(var value:saved.get("ownedAccountIds")) { var user=users.selectById(value.asLong()); assertNotNull(user); assertTrue(user.loginName.matches("b07_ui_(admin|reporter|tech_a|tech_b|other|unqualified)")); }
        time.set(Instant.now().truncatedTo(ChronoUnit.MILLIS));
        try {
            long deadline=System.nanoTime()+Duration.ofMinutes(30).toNanos();
            while(!Files.exists(control.resolve("done")) && System.nanoTime()<deadline) Thread.sleep(250);
            assertTrue(Files.exists(control.resolve("done")),"Resumed fixture timed out");
        } finally { recover_interrupted_fixture(); }
    }
    @Test void recover_interrupted_fixture() throws Exception {
        Path project=Path.of("..").toAbsolutePath().normalize(); var saved=json.readTree(Files.readString(evidence(project,"browser-control")));
        assertEquals("test",saved.get("profile").asString()); assertEquals("campus_maintenance_test",saved.get("database").asString());
        var ids=new ArrayList<Long>(); for(var value:saved.get("ownedAccountIds")) { assertTrue(value.asLong()>0); ids.add(value.asLong()); }
        var aliases=new HashSet<Long>(); for(var value:saved.get("accounts").properties()) aliases.add(Long.parseLong(value.getValue().asString())); assertEquals(new HashSet<>(ids),aliases); assertEquals(ids.size(),aliases.size());
        for(long id:ids) { var user=users.selectById(id); if(user!=null) assertTrue(user.loginName.matches("b07_ui_(admin|reporter|tech_a|tech_b|other|unqualified)")); }
        Path control=Path.of(saved.get("controlDirectory").asString()).toAbsolutePath().normalize(); assertEquals(attachmentRoot,control.getParent()); assertTrue(control.getFileName().toString().matches("b07-browser-[0-9a-f-]{36}"));
        if(Files.exists(control,LinkOption.NOFOLLOW_LINKS)) { assertTrue(Files.isDirectory(control,LinkOption.NOFOLLOW_LINKS)); assertEquals(attachmentRoot,control.toRealPath().getParent()); }
        owned.addAll(ids); var campusIds=saved.get("ownedCampusIds");
        for(var v:campusIds.get("buildings")) buildings.add(v.asLong()); for(var v:campusIds.get("floors")) floors.add(v.asLong()); for(var v:campusIds.get("locations")) locations.add(v.asLong()); for(var v:campusIds.get("equipment")) equipment.add(v.asLong());
        Files.writeString(evidence(project,"browser-interrupted-db"),json.writeValueAsString(db.queryForList("SELECT id,equipment_id,status,version,current_round,assigned_technician_id,accepted_technician_id FROM work_order WHERE reporter_id IN ("+marks(ids.size())+") ORDER BY id",ids.toArray())));
        var proof=new LinkedHashMap<String,Object>();
        proof.put("orders",db.queryForList("SELECT id,request_id,equipment_id,status,version,current_round,assigned_technician_id,accepted_technician_id,CAST(accepted_at AS CHAR) accepted_at,CAST(completed_at AS CHAR) completed_at FROM work_order WHERE reporter_id IN ("+marks(ids.size())+") ORDER BY id",ids.toArray()));
        proof.put("rounds",db.queryForList("SELECT r.work_order_id,r.round_no,r.technician_id,r.result_text,r.acceptance_passed,r.acceptance_reason FROM repair_round r JOIN work_order w ON w.id=r.work_order_id WHERE w.reporter_id IN ("+marks(ids.size())+") ORDER BY r.work_order_id,r.round_no",ids.toArray()));
        proof.put("events",db.queryForList("SELECT e.work_order_id,e.event_type,e.operation_type,e.operation_id,e.result_version,e.result_round FROM work_order_event e JOIN work_order w ON w.id=e.work_order_id WHERE w.reporter_id IN ("+marks(ids.size())+") ORDER BY e.id",ids.toArray()));
        proof.put("requests",db.queryForList("SELECT id,status,version,location_id FROM repair_request WHERE reporter_id IN ("+marks(ids.size())+") ORDER BY id",ids.toArray()));
        Files.writeString(evidence(project,"browser-final-db"),json.writeValueAsString(proof));
        b04Cleanup(); cleanupOwnedOnly();
        for(var v:campusIds.get("initialization")) assertEquals(1,db.update("DELETE FROM campus_initialization WHERE id=?",v.asLong()));
        if(Files.exists(control,LinkOption.NOFOLLOW_LINKS)) { for(String name:List.of("ready","done","B07-proof-1.png","B07-proof-2.png")) { var file=control.resolve(name); if(Files.exists(file,LinkOption.NOFOLLOW_LINKS)) { assertTrue(Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)); assertEquals(control.toRealPath(),file.toRealPath().getParent()); Files.delete(file); } } Files.delete(control); }
        assertFalse(Files.exists(control)); for(long id:ids) assertNull(users.selectById(id));
    }
    private void fixture(boolean failBeforeReady) throws Exception {
        Path project=Path.of("..").toAbsolutePath().normalize(), control=null;
        var snapshots=new ArrayList<Long>(); boolean ready=false;
        try {
            for(String table:List.of("user_account","campus_building","campus_floor","campus_location","equipment","campus_initialization","attachment_file","repair_request","work_order"))
                assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM "+table,Integer.class),"Fixture refuses unknown existing test data");
            control=Files.createDirectory(attachmentRoot.resolve("b07-browser-"+UUID.randomUUID())); lastControl=control;
            assertEquals(attachmentRoot,control.toRealPath().getParent()); time.set(Instant.now().truncatedTo(ChronoUnit.MILLIS));
            assertTrue(campus.initialize(project.resolve("config/campus.json")));
            buildings.addAll(db.queryForList("SELECT id FROM campus_building",Long.class)); floors.addAll(db.queryForList("SELECT id FROM campus_floor",Long.class)); locations.addAll(db.queryForList("SELECT id FROM campus_location",Long.class)); equipment.addAll(db.queryForList("SELECT id FROM equipment",Long.class)); snapshots.addAll(db.queryForList("SELECT id FROM campus_initialization",Long.class));
            var accounts=new LinkedHashMap<String,UserEntity>();
            for(var entry:List.of(new String[]{"admin","ADMIN","B07演示管理员"},new String[]{"reporter","REPORTER","B07原报修人"},new String[]{"tech_a","TECHNICIAN","B07维修甲"},new String[]{"tech_b","TECHNICIAN","B07维修乙"},new String[]{"other","REPORTER","B07其他报修人"},new String[]{"unqualified","TECHNICIAN","B07待审维修人员"})) {
                var role=CurrentUser.Role.valueOf(entry[1]); var user=account(role,role==CurrentUser.Role.TECHNICIAN?CurrentUser.ReviewStatus.APPROVED:null);
                if(entry[0].equals("unqualified")) { user.reviewStatus=CurrentUser.ReviewStatus.PENDING; db.update("UPDATE user_account SET review_status='PENDING' WHERE id=?",user.id); }
                user.loginName="b07_ui_"+entry[0]; db.update("UPDATE user_account SET login_name=?,name=? WHERE id=?",user.loginName,entry[2],user.id); accounts.put(entry[0],user);
            }
            var imagePaths=new ArrayList<String>();
            for(int n=1;n<=2;n++) {
                Path image=control.resolve("B07-proof-"+n+".png"); var bitmap=new BufferedImage(320,240,BufferedImage.TYPE_INT_RGB); var g=bitmap.createGraphics();
                g.setColor(n==1?new java.awt.Color(21,94,117):new java.awt.Color(90,120,55)); g.fillRect(0,0,320,240); g.setColor(java.awt.Color.WHITE); g.drawString("B07 synthetic round "+n,70,115); g.dispose(); assertTrue(ImageIO.write(bitmap,"png",image.toFile())); imagePaths.add(image.toString());
            }
            if(failBeforeReady) throw new IllegalStateException("Expected ready-before failure after owned data and files");
            var owner=b04Browser(); owner.login(accounts.get("reporter")); var manager=b04Browser(); manager.login(accounts.get("admin"));
            var orders=new LinkedHashMap<String,String>();
            var requests=new LinkedHashMap<String,String>();
            var other=b04Browser(); other.login(accounts.get("other"));
            String[][] cases={{"文博1同号","wb1-f1-r101"},{"文博2同号","wb2-f1-r101"},{"文博3同号","wb3-f1-r101"},{"其他报修人","wb1-f1-r101"},{"投影仪","zy-f1-r101"},{"食堂分组","ct1-f2-area"},{"体育馆分组","gym-f2-gallery"}};
            for(var entry:cases) {
                var browser=entry[0].equals("其他报修人")?other:owner;
                long location=db.queryForObject("SELECT id FROM campus_location WHERE code=?",Long.class,entry[1]);
                var body=createBody(upload(browser),location,UUID.randomUUID().toString()); body.put("equipmentName",entry[0].equals("投影仪")?"投影仪":"B07浏览器"+entry[0]); body.put("description","隔离测试原文："+entry[0]);
                var created=browser.post("/api/repair-requests",body); assertEquals(201,created.statusCode(),created.body()); String request=data(created).get("resourceId").asString(); requests.put(entry[0],request);
            }
            var metadata=new LinkedHashMap<String,Object>(); metadata.put("profile","test"); metadata.put("database","campus_maintenance_test"); metadata.put("backendPort",port); metadata.put("controlDirectory",control.toString()); metadata.put("imageFiles",imagePaths); metadata.put("orders",orders); metadata.put("requests",requests);
            metadata.put("accounts",accounts.entrySet().stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,e -> e.getValue().id.toString()))); metadata.put("ownedAccountIds",List.copyOf(owned));
            metadata.put("ownedCampusIds",Map.of("buildings",List.copyOf(buildings),"floors",List.copyOf(floors),"locations",List.copyOf(locations),"equipment",List.copyOf(equipment),"initialization",snapshots));
            Files.writeString(evidence(project,"browser-control"),json.writeValueAsString(metadata)); Files.writeString(control.resolve("ready"),"ready",StandardOpenOption.CREATE_NEW); ready=true;
            long deadline=System.nanoTime()+Duration.ofMinutes(180).toNanos();
            while(!Files.exists(control.resolve("done")) && System.nanoTime()<deadline) Thread.sleep(250);
            assertTrue(Files.exists(control.resolve("done")),"B07 fixture timed out; finally still cleans owned data");
        } finally {
            try { if(ready) {
                var proof=new LinkedHashMap<String,Object>();
                proof.put("requests",db.queryForList("SELECT id,request_no,status,version,location_id,first_submitted_at FROM repair_request WHERE reporter_id IN ("+marks(owned.size())+") ORDER BY id",owned.toArray()));
                proof.put("orders",db.queryForList("SELECT id,work_order_no,equipment_id,request_id,status,version,current_round,assigned_technician_id,accepted_technician_id,CAST(accepted_at AS CHAR) accepted_at,CAST(completed_at AS CHAR) completed_at FROM work_order WHERE reporter_id IN ("+marks(owned.size())+") ORDER BY id",owned.toArray()));
                proof.put("rounds",db.queryForList("SELECT r.work_order_id,r.round_no,r.technician_id,r.result_text,r.acceptance_passed,r.acceptance_reason,CAST(r.started_at AS CHAR) started_at,CAST(r.submitted_at AS CHAR) submitted_at,CAST(r.acceptance_at AS CHAR) acceptance_at FROM repair_round r JOIN work_order w ON w.id=r.work_order_id WHERE w.reporter_id IN ("+marks(owned.size())+") ORDER BY r.work_order_id,r.round_no",owned.toArray()));
                proof.put("events",db.queryForList("SELECT e.work_order_id,e.event_type,e.operation_type,e.operation_id,e.result_version,e.result_round FROM work_order_event e JOIN work_order w ON w.id=e.work_order_id WHERE w.reporter_id IN ("+marks(owned.size())+") ORDER BY e.id",owned.toArray()));
                proof.put("attachments",db.queryForList("SELECT id,purpose,state,target_work_order_id,target_round,bound_round_id,byte_size,HEX(file_digest) sha256 FROM attachment_file WHERE uploader_id IN ("+marks(owned.size())+") ORDER BY id",owned.toArray()));
                Files.writeString(evidence(project,"browser-final-db"),json.writeValueAsString(proof));
            } } finally {
            b04Cleanup(); cleanupOwnedOnly();
            for(long id:snapshots) assertEquals(1,db.update("DELETE FROM campus_initialization WHERE id=?",id));
            if(control!=null && Files.exists(control,LinkOption.NOFOLLOW_LINKS)) {
                assertEquals(attachmentRoot,control.toRealPath().getParent());
                for(String name:List.of("ready","done","B07-proof-1.png","B07-proof-2.png")) { var file=control.resolve(name); if(Files.exists(file,LinkOption.NOFOLLOW_LINKS)) { assertTrue(Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)); assertEquals(control.toRealPath(),file.toRealPath().getParent()); Files.delete(file); } }
                Files.delete(control);
            }
            }
        }
    }
}
