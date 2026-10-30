package cn.wzbc.maintenance.support;

import cn.wzbc.maintenance.account.entity.UserEntity;
import cn.wzbc.maintenance.config.TestDatabaseGuard;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import static org.junit.jupiter.api.Assertions.*;

/** Real database/HTTP/files. Cleanup is limited to this fixture's IDs and registered files. */
public abstract class B04HttpTestSupport extends B02HttpTestSupport {
    @Autowired protected Environment environment;
    @Autowired protected PlatformTransactionManager transactions;
    protected final List<Long> buildings=new ArrayList<>(), floors=new ArrayList<>(), locations=new ArrayList<>(), equipment=new ArrayList<>();
    protected long locationA, locationB, observation;
    protected Path attachmentRoot;
    @BeforeEach void b04Fixture() throws Exception {
        attachmentRoot=Path.of(environment.getRequiredProperty("campus.attachment-root")).toRealPath();
        assertEquals(Path.of(environment.getRequiredProperty("campus.project-root")).toRealPath().resolve("data/attachments/test"),attachmentRoot);
        if(environment.getProperty("campus.browser-harness",Boolean.class,false)) return;
        long b=IdWorker.getId(), f=IdWorker.getId(); String code="b04-"+b;
        db.update("INSERT INTO campus_building(id,created_at,code,name,type,display_order,config_version) VALUES(?,?,?,'B04演示','DORMITORY',0,'campus-v1')",b,time.instant(),code); buildings.add(b);
        db.update("INSERT INTO campus_floor(id,created_at,building_id,level,code,name,display_order,config_version) VALUES(?,?,?,1,?,'首层',0,'campus-v1')",f,time.instant(),b,code+"-f1"); floors.add(f);
        locationA=location(f,code+"-a","101","DORM_ROOM",true);
        locationB=location(f,code+"-b","102","DORM_ROOM",true);
        observation=location(f,code+"-lobby","门厅","DORM_LOBBY",false);
    }
    protected long location(long floor,String code,String name,String use,boolean reportable) {
        long id=IdWorker.getId();
        db.update("INSERT INTO campus_location(id,created_at,floor_id,code,type,space_use,name,reportable,display_order,config_version,version,updated_at) VALUES(?,?,?,?,'ROOM',?,?,?,0,'campus-v1',0,?)",id,time.instant(),floor,code,use,name,reportable,time.instant());
        locations.add(id); return id;
    }
    protected boolean table(String name) { return db.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name=?",Integer.class,name)>0; }
    protected static String marks(int count) { return String.join(",",Collections.nCopies(count,"?")); }
    protected void delete(String table,String column,List<Long> ids) { if (!ids.isEmpty() && table(table)) db.update("DELETE FROM "+table+" WHERE "+column+" IN ("+marks(ids.size())+")",ids.toArray()); }
    @AfterEach void b04Cleanup() throws Exception {
        try(var c=datasource.getConnection()) { TestDatabaseGuard.validateConnection(c,"test"); }
        if (!owned.isEmpty()) {
            // API-created equipment is attributable through the fixture's registered actors.
            // Do not infer ownership merely because an unknown item shares a fixture location.
            if(table("equipment_event")) {
                var added=db.queryForList("SELECT DISTINCT equipment_id FROM equipment_event WHERE actor_user_id IN ("+marks(owned.size())+")",Long.class,owned.toArray());
                for(long id:added) if(!equipment.contains(id)) equipment.add(id);
            }
            if (table("attachment_file")) {
                var files=db.queryForList("SELECT storage_key FROM attachment_file WHERE uploader_id IN ("+marks(owned.size())+")",String.class,owned.toArray());
                for(String key:files) {
                    assertTrue(key.matches("[0-9a-f-]{36}\\.(png|jpg)")); Path file=attachmentRoot.resolve(key);
                    assertEquals(attachmentRoot,file.toAbsolutePath().normalize().getParent());
                    if (Files.exists(file,LinkOption.NOFOLLOW_LINKS)) { assertTrue(Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)); assertEquals(attachmentRoot,file.toRealPath().getParent()); Files.delete(file); }
                }
                delete("attachment_file","uploader_id",owned);
            }
            if(table("work_order")) {
                var orders=db.queryForList("SELECT id FROM work_order WHERE reporter_id IN ("+marks(owned.size())+")",Long.class,owned.toArray());
                if(!orders.isEmpty()) {
                    db.update("UPDATE work_order SET current_assignment_id=NULL WHERE id IN ("+marks(orders.size())+")",orders.toArray());
                    for(String t:List.of("work_order_event","repair_progress","repair_round","work_order_assignment")) delete(t,"work_order_id",orders);
                    delete("work_order","id",orders);
                }
            }
            if(table("repair_request")) {
                var requests=db.queryForList("SELECT id FROM repair_request WHERE reporter_id IN ("+marks(owned.size())+")",Long.class,owned.toArray());
                if(!requests.isEmpty()) {
                    db.update("UPDATE repair_request SET current_submission_id=NULL WHERE id IN ("+marks(requests.size())+")",requests.toArray());
                    delete("repair_request_event","request_id",requests); delete("repair_request_submission","request_id",requests); delete("repair_request","id",requests);
                }
            }
            if(table("equipment_event")) {
                var events=db.queryForList("SELECT id FROM equipment_event WHERE actor_user_id IN ("+marks(owned.size())+")",Long.class,owned.toArray());
                delete("equipment_location_history","equipment_event_id",events); delete("equipment_event","id",events);
            }
        }
        // Unknown history prevents deletion via real foreign keys instead of being swept.
        delete("equipment","id",equipment);
        delete("campus_location","id",locations); delete("campus_floor","id",floors); delete("campus_building","id",buildings);
        equipment.clear(); locations.clear(); floors.clear(); buildings.clear();
    }
    protected byte[] png() throws Exception {
        var out=new ByteArrayOutputStream(); var image=new BufferedImage(3,2,BufferedImage.TYPE_INT_RGB);
        image.setRGB(1,1,0x155e75); assertTrue(ImageIO.write(image,"png",out)); return out.toByteArray();
    }
    protected B04Browser b04Browser() { return new B04Browser(); }
    protected class B04Browser extends Browser {
        public HttpResponse<String> upload(Map<String,String> fields,String name,String mime,byte[] bytes,boolean withCsrf) throws Exception {
            String boundary="B04"+UUID.randomUUID(); var out=new ByteArrayOutputStream();
            for(var item:fields.entrySet()) out.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\""+item.getKey()+"\"\r\n\r\n"+item.getValue()+"\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\""+name+"\"\r\nContent-Type: "+mime+"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(bytes); out.write(("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.US_ASCII));
            var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/attachments/temp")).timeout(java.time.Duration.ofSeconds(15)).header("Content-Type","multipart/form-data; boundary="+boundary).POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray()));
            if(withCsrf) request.header("X-CSRF-TOKEN",csrf);
            return client.send(request.build(),HttpResponse.BodyHandlers.ofString());
        }
        public HttpResponse<byte[]> content(String id) throws Exception { return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/attachments/"+id+"/content")).GET().build(),HttpResponse.BodyHandlers.ofByteArray()); }
        public HttpResponse<String> cancel(String id,String operation) throws Exception {
            return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/attachments/"+id+"/temp")).header("X-CSRF-TOKEN",csrf).header("X-Operation-Id",operation).DELETE().build(),HttpResponse.BodyHandlers.ofString());
        }
    }
    protected Map<String,String> faultContext() { return Map.of("operationId",UUID.randomUUID().toString(),"purpose","REQUEST_FAULT","batchId",UUID.randomUUID().toString()); }
    protected String upload(B04Browser b) throws Exception { var r=b.upload(faultContext(),"fault.png","image/png",png(),true); assertEquals(201,r.statusCode(),r.body()); return data(r).get("id").asString(); }
    protected Map<String,Object> createBody(String attachment,long location,String operation) {
        return new LinkedHashMap<>(Map.of("locationId",Long.toString(location),"equipmentName","演示空调","addressText","用户原文地址","description","演示故障原文","contactName","本次联系人","contactPhone","13800000000","attachmentIds",List.of(attachment),"operationId",operation));
    }
}
