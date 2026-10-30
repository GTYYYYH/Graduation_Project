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

/** Explicit B09-only browser fixture. No HTTP test endpoints; excluded from default verify. */
@SpringBootTest(classes={Application.class,B02HttpTestSupport.TimeConfig.class},webEnvironment=SpringBootTest.WebEnvironment.DEFINED_PORT,
    properties={"server.port=8081","campus.browser-harness=true"})
class B09BrowserHarness extends B04HttpTestSupport {
    @Autowired CampusInitializationService campus;
    private Path lastControl;
    private Path evidence(Path project,String suffix) {
        String prefix=environment.getProperty("campus.browser-evidence-prefix","B09");
        assertTrue(prefix.matches("B09(?:-[a-z0-9]+)*"));
        return project.resolve("tests/evidence/"+prefix+"-"+suffix+".json");
    }
    @Test void isolated_browser_fixture_lifecycle() throws Exception { fixture(false); }
    private void fixture(boolean failBeforeReady) throws Exception {
        Path project=Path.of("..").toAbsolutePath().normalize(), control=null;
        var snapshots=new ArrayList<Long>(); boolean ready=false;
        try {
            for(String table:List.of("user_account","campus_building","campus_floor","campus_location","equipment","campus_initialization","attachment_file","repair_request","work_order"))
                assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM "+table,Integer.class),"Fixture refuses unknown existing test data");
            control=Files.createDirectory(attachmentRoot.resolve("b09-browser-"+UUID.randomUUID())); lastControl=control;
            assertEquals(attachmentRoot,control.toRealPath().getParent()); time.set(Instant.parse("2026-10-25T06:00:00Z"));
            assertTrue(campus.initialize(project.resolve("config/campus.json")));
            buildings.addAll(db.queryForList("SELECT id FROM campus_building",Long.class)); floors.addAll(db.queryForList("SELECT id FROM campus_floor",Long.class)); locations.addAll(db.queryForList("SELECT id FROM campus_location",Long.class)); equipment.addAll(db.queryForList("SELECT id FROM equipment",Long.class)); snapshots.addAll(db.queryForList("SELECT id FROM campus_initialization",Long.class));
            var accounts=new LinkedHashMap<String,UserEntity>();
            for(String alias:java.util.stream.Stream.concat(java.util.stream.Stream.of("admin","reporter","tech_a","tech_b","tech_c","tech_d","tech_e","tech_f","tech_g","reporter_empty"),java.util.stream.IntStream.rangeClosed(1,20).mapToObj(n->"reporter_extra"+n)).toList()) {
                var role=alias.equals("admin")?CurrentUser.Role.ADMIN:alias.startsWith("reporter")?CurrentUser.Role.REPORTER:CurrentUser.Role.TECHNICIAN;
                var user=account(role,role==CurrentUser.Role.TECHNICIAN?CurrentUser.ReviewStatus.APPROVED:null);
                user.loginName="b09_ui_"+alias;user.name="B09演示"+alias;
                db.update("UPDATE user_account SET login_name=?,name=? WHERE id=?",user.loginName,user.name,user.id);accounts.put(alias,user);
            }
            for(var entry:accounts.entrySet()) {
                var u=entry.getValue(); if(u.role!=CurrentUser.Role.TECHNICIAN)continue;
                String status=entry.getKey().equals("tech_f")?"PENDING":entry.getKey().equals("tech_g")?"REJECTED":"APPROVED";
                db.update("UPDATE user_account SET review_status=? WHERE id=?",status,u.id);
                if(entry.getKey().equals("tech_e"))db.update("UPDATE user_account SET enabled=0 WHERE id=?",u.id);
                boolean reviewed=!status.equals("PENDING");
                db.update("INSERT INTO registration_review(id,created_at,user_id,attempt,name_snapshot,phone_snapshot,submitted_at,status,reviewed_by,reviewed_at,reason) VALUES(?,?,?,1,?,?,?,?,?,?,?)",com.baomidou.mybatisplus.core.toolkit.IdWorker.getId(),time.instant(),u.id,u.name,u.phone,time.instant(),status,reviewed?accounts.get("admin").id:null,reviewed?time.instant():null,status.equals("REJECTED")?"B09合成资料需要补充":null);
            }
            var imagePaths=new ArrayList<String>();
            for(int n=1;n<=2;n++) {
                Path image=control.resolve("B09-proof-"+n+".png"); var bitmap=new BufferedImage(320,240,BufferedImage.TYPE_INT_RGB); var g=bitmap.createGraphics();
                g.setColor(n==1?new java.awt.Color(21,94,117):new java.awt.Color(90,120,55)); g.fillRect(0,0,320,240); g.setColor(java.awt.Color.WHITE); g.drawString("B09 synthetic round "+n,70,115); g.dispose(); assertTrue(ImageIO.write(bitmap,"png",image.toFile())); imagePaths.add(image.toString());
            }
            if(failBeforeReady) throw new IllegalStateException("Expected ready-before failure after owned data and files");
            var owner=b04Browser(); owner.login(accounts.get("reporter")); var manager=b04Browser(); manager.login(accounts.get("admin"));
            var orders=new LinkedHashMap<String,String>();
            var requests=new LinkedHashMap<String,String>();
            for(String label:java.util.stream.Stream.concat(java.util.stream.Stream.of("first","pending","refused","spare","complete"),java.util.stream.IntStream.rangeClosed(1,24).mapToObj(n->"history_"+n)).toList()) {
                long location=db.queryForObject("SELECT id FROM campus_location WHERE code='wb1-f1-r101'",Long.class);
                var body=createBody(upload(owner),location,UUID.randomUUID().toString());body.put("equipmentName","B09演示"+label);
                var created=owner.post("/api/repair-requests",body);assertEquals(201,created.statusCode(),created.body());
                String req=data(created).get("resourceId").asString();requests.put(label,req);
                if(label.equals("complete")) {
                    long device=db.queryForObject("SELECT id FROM equipment WHERE location_id=? ORDER BY id LIMIT 1",Long.class,location);
                    long completed=new B04WorkOrderFixture(db,time.instant()).order(Long.parseLong(req),device,accounts.get("tech_a").id,accounts.get("admin").id,"COMPLETED");orders.put(label,Long.toString(completed));
                }
                if(label.equals("pending") || label.equals("refused")) {
                    var tech=accounts.get(label.equals("pending")?"tech_a":"tech_b");
                    var result=manager.post("/api/repair-requests/"+req+"/approve-and-assign",Map.of("version",0,"technicianId",tech.id.toString(),"confirmPreviouslyRefused",false,"operationId",UUID.randomUUID().toString()));assertEquals(200,result.statusCode(),result.body());
                    String order=data(result).get("relatedResourceId").asString();orders.put(label,order);
                    if(label.equals("refused")) {var worker=b04Browser();worker.login(tech);assertEquals(200,worker.post("/api/work-orders/"+order+"/refuse",Map.of("version",0,"reason","B09已登记拒单","operationId",UUID.randomUUID().toString())).statusCode());}
                }
            }
            var metadata=new LinkedHashMap<String,Object>(); metadata.put("profile","test"); metadata.put("database","campus_maintenance_test"); metadata.put("backendPort",port); metadata.put("controlDirectory",control.toString()); metadata.put("imageFiles",imagePaths); metadata.put("orders",orders); metadata.put("requests",requests);
            metadata.put("accounts",accounts.entrySet().stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,e -> e.getValue().id.toString()))); metadata.put("ownedAccountIds",List.copyOf(owned));
            metadata.put("ownedCampusIds",Map.of("buildings",List.copyOf(buildings),"floors",List.copyOf(floors),"locations",List.copyOf(locations),"equipment",List.copyOf(equipment),"initialization",snapshots));
            Files.writeString(evidence(project,"browser-control"),json.writeValueAsString(metadata)); Files.writeString(control.resolve("ready"),"ready",StandardOpenOption.CREATE_NEW); ready=true;
            long deadline=System.nanoTime()+Duration.ofMinutes(180).toNanos();
            while(!Files.exists(control.resolve("done")) && System.nanoTime()<deadline) {
                if(Files.exists(control.resolve("clock-next"))) {time.set(Instant.parse("2026-10-24T16:00:10Z"));Files.delete(control.resolve("clock-next"));Files.writeString(control.resolve("clock-applied"),time.instant().toString());}
                Thread.sleep(250);
            }
            assertTrue(Files.exists(control.resolve("done")),"B09 fixture timed out; finally still cleans owned data");
        } finally {
            try { if(ready) {
                var proof=new LinkedHashMap<String,Object>();
                proof.put("accounts",db.queryForList("SELECT id,login_name,name,phone,role,enabled,review_status,version FROM user_account WHERE id IN ("+marks(owned.size())+") ORDER BY id",owned.toArray()));
                proof.put("accountEvents",db.queryForList("SELECT actor_user_id,target_user_id,event_type,operation_type,operation_id,result_version FROM account_event WHERE target_user_id IN ("+marks(owned.size())+") ORDER BY id",owned.toArray()));
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
                for(String name:List.of("ready","done","B09-proof-1.png","B09-proof-2.png","clock-next","clock-applied")) { var file=control.resolve(name); if(Files.exists(file,LinkOption.NOFOLLOW_LINKS)) { assertTrue(Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)); assertEquals(control.toRealPath(),file.toRealPath().getParent()); Files.delete(file); } }
                Files.delete(control);
            }
            }
        }
    }
}
