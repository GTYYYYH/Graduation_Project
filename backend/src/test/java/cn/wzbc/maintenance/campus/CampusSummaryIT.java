package cn.wzbc.maintenance.campus;

import cn.wzbc.maintenance.account.entity.UserEntity;
import cn.wzbc.maintenance.security.CurrentUser;
import cn.wzbc.maintenance.support.B04HttpTestSupport;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CopyOnWriteArrayList;
import static org.junit.jupiter.api.Assertions.*;

/** Real Cookie/CSRF/MySQL/file tests; never count another reporter or past assignment. */
@Import(CampusSummaryIT.ProbeConfig.class)
class CampusSummaryIT extends B04HttpTestSupport {
    @Autowired SqlProbe probe;
    @TestConfiguration(proxyBeanMethods=false) static class ProbeConfig { @Bean SqlProbe campusSqlProbe() { return new SqlProbe(); } }
    @Intercepts(@Signature(type=Executor.class,method="query",args={MappedStatement.class,Object.class,RowBounds.class,ResultHandler.class}))
    public static class SqlProbe implements Interceptor {
        final AtomicReference<Runnable> afterAggregate=new AtomicReference<>();
        final List<String> statements=new CopyOnWriteArrayList<>();
        volatile boolean measuring;
        @Override public Object intercept(Invocation invocation) throws Throwable {
            String name=((MappedStatement)invocation.getArgs()[0]).getId();
            if(measuring && name.contains(".campus.mapper.")) {
                var connection=((Executor)invocation.getTarget()).getTransaction().getConnection();
                assertEquals(java.sql.Connection.TRANSACTION_REPEATABLE_READ,connection.getTransactionIsolation()); assertTrue(connection.isReadOnly());
                statements.add(name);
            }
            Object result=invocation.proceed();
            if(name.endsWith("CampusBusinessMapper.aggregate")) {var hook=afterAggregate.getAndSet(null); if(hook!=null)hook.run();}
            return result;
        }
    }
    private String op() { return UUID.randomUUID().toString(); }
    private B04Browser login(UserEntity user) throws Exception { var b=b04Browser(); b.login(user); return b; }
    private long create(B04Browser owner,long location) throws Exception {
        var r=owner.post("/api/repair-requests",createBody(upload(owner),location,op()));
        assertEquals(201,r.statusCode(),r.body()); return Long.parseLong(data(r).get("resourceId").asString());
    }
    private JsonNode summaries(B04Browser b) throws Exception { var r=b.get("/api/campus/summaries"); assertEquals(200,r.statusCode(),r.body()); assertTrue(r.headers().firstValue("Cache-Control").orElse("").contains("no-store")); return data(r); }
    private JsonNode at(B04Browser b,long location) throws Exception { for(var row:summaries(b).get("locations")) if(row.get("locationId").asString().equals(Long.toString(location))) return row; fail("Expected visible location"); return null; }
    private JsonNode items(B04Browser b,long location,String query) throws Exception { var r=b.get("/api/campus/locations/"+location+"/items"+query); assertEquals(200,r.statusCode(),r.body()); return data(r); }
    private record Summary(int visibleTotal) {}
    @Test void same_location_only_counts_owned_items() throws Exception {
        var first=login(account(CurrentUser.Role.REPORTER,null)); var second=login(account(CurrentUser.Role.REPORTER,null)); var admin=login(account(CurrentUser.Role.ADMIN,null));
        long a=create(first,locationA), b=create(second,locationA);
        Summary reporterSummary=new Summary(at(first,locationA).get("visibleTotal").asInt());
        assertEquals(1, reporterSummary.visibleTotal());
        assertEquals(1,at(second,locationA).get("visibleTotal").asInt()); assertEquals(2,at(admin,locationA).get("visibleTotal").asInt());
        var firstItems=items(first,locationA,""); assertEquals(1,firstItems.get("items").get("total").asInt()); assertEquals(Long.toString(a),firstItems.get("items").get("records").get(0).get("id").asString());
        assertEquals(Long.toString(b),items(second,locationA,"").get("items").get("records").get(0).get("id").asString());
        assertEquals("PENDING_REVIEW",at(first,locationA).get("category").asString());
    }
    private long approve(B04Browser admin,long request,long worker) throws Exception {
        var r=admin.post("/api/repair-requests/"+request+"/approve-and-assign",Map.of("version",0,"technicianId",Long.toString(worker),"confirmPreviouslyRefused",false,"operationId",op()));
        assertEquals(200,r.statusCode(),r.body()); return Long.parseLong(data(r).get("relatedResourceId").asString());
    }
    @Test void approved_request_counts_once_and_unowned_red_never_changes_reporter_category() throws Exception {
        var reporter=login(account(CurrentUser.Role.REPORTER,null)); var other=login(account(CurrentUser.Role.REPORTER,null)); var manager=login(account(CurrentUser.Role.ADMIN,null));
        var worker=account(CurrentUser.Role.TECHNICIAN,CurrentUser.ReviewStatus.APPROVED); var tech=login(worker);
        long mine=create(reporter,locationA), theirs=create(other,locationA); long order=approve(manager,theirs,worker.id);
        assertEquals("PENDING_REVIEW",at(reporter,locationA).get("category").asString());
        assertEquals(1,at(other,locationA).get("visibleTotal").asInt()); assertEquals(2,at(manager,locationA).get("visibleTotal").asInt());
        assertEquals(1,at(tech,locationA).get("visibleTotal").asInt()); assertEquals("WORK_ORDER",items(tech,locationA,"").get("items").get("records").get(0).get("resourceType").asString());
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM work_order WHERE id=? AND equipment_id IS NOT NULL",Integer.class,order));
        assertEquals(200,tech.post("/api/work-orders/"+order+"/refuse",Map.of("version",0,"reason","本次无法处理","operationId",op())).statusCode());
        assertEquals(0,summaries(tech).get("locations").size()); assertEquals(0,items(tech,locationA,"").get("visibleTotal").asInt());
        assertEquals(1,at(manager,locationA).get("counts").get("reassignPending").asInt());
        assertEquals(200,tech.get("/api/work-orders/"+order).statusCode()); // finite history remains separately available
        assertEquals(Long.toString(mine),items(reporter,locationA,"").get("items").get("records").get(0).get("id").asString());
    }
    @Test void stages_are_mutually_exclusive_and_completion_only_removes_its_order() throws Exception {
        var user=account(CurrentUser.Role.REPORTER,null); var owner=login(user); var manager=login(account(CurrentUser.Role.ADMIN,null)); var worker=account(CurrentUser.Role.TECHNICIAN,CurrentUser.ReviewStatus.APPROVED); var tech=login(worker);
        create(owner,locationA); long order=approve(manager,create(owner,locationA),worker.id);
        assertEquals(200,tech.post("/api/work-orders/"+order+"/accept",Map.of("version",0,"operationId",op())).statusCode());
        assertEquals(1,at(owner,locationA).get("counts").get("inProgress").asInt());
        submit(tech,order,1,1);
        assertEquals("PENDING_ACCEPTANCE",at(owner,locationA).get("category").asString());
        assertEquals(1,at(tech,locationA).get("visibleTotal").asInt()); assertEquals(0,data(tech.get("/api/work-orders/navigation-count")).get("count").asInt());
        assertEquals(200,owner.post("/api/work-orders/"+order+"/acceptance",Map.of("version",2,"round",1,"passed",false,"reason","仍未修好","operationId",op())).statusCode());
        var counts=at(owner,locationA).get("counts"); assertEquals(1,counts.get("rework").asInt()); assertEquals(0,counts.get("inProgress").asInt());
        submit(tech,order,3,2);
        var body=new LinkedHashMap<String,Object>(Map.of("version",4,"round",2,"passed",true,"operationId",op())); body.put("reason",null);
        assertEquals(200,owner.post("/api/work-orders/"+order+"/acceptance",body).statusCode());
        assertEquals(1,at(owner,locationA).get("visibleTotal").asInt()); assertEquals("PENDING_REVIEW",at(owner,locationA).get("category").asString()); assertEquals(0,summaries(tech).get("locations").size());
    }
    private void submit(B04Browser tech,long order,int version,int round) throws Exception {
        var image=tech.upload(Map.of("operationId",op(),"purpose","REPAIR_VOUCHER","workOrderId",Long.toString(order),"round",Integer.toString(round)),"proof.png","image/png",png(),true);
        assertEquals(201,image.statusCode()); assertEquals(200,tech.post("/api/work-orders/"+order+"/submit-result",Map.of("operationId",op(),"version",version,"round",round,"result","本轮新结果","attachmentIds",List.of(data(image).get("id").asString()))).statusCode());
    }
    @Test void filters_pagination_allowed_actions_and_shared_query_time_follow_visible_scope() throws Exception {
        var owner=login(account(CurrentUser.Role.REPORTER,null)); var manager=login(account(CurrentUser.Role.ADMIN,null)); long a=create(owner,locationA); create(owner,locationA); create(owner,locationB);
        assertEquals(200,manager.post("/api/repair-requests/"+a+"/return",Map.of("version",0,"reason","需补充","operationId",op())).statusCode());
        var root=summaries(owner); for(var row:root.get("locations")) assertEquals(root.get("queriedAt").asString(),row.get("queriedAt").asString());
        var found=items(owner,locationA,"?category=NEEDS_SUPPLEMENT&page=1&pageSize=1"); assertEquals(2,found.get("visibleTotal").asInt()); assertEquals(1,found.get("items").get("total").asInt()); assertEquals("needsSupplement",found.get("items").get("records").get(0).get("stage").asString());
        assertTrue(found.get("items").get("records").get(0).get("allowedActions").toString().contains("SUPPLEMENT_REQUEST"));
        assertEquals(0,items(owner,locationA,"?category=NEEDS_SUPPLEMENT&page=2&pageSize=1").get("items").get("records").size());
        assertEquals(0,items(owner,observation,"").get("items").get("total").asInt());
        String floor=Long.toString(floors.getFirst()), building=Long.toString(buildings.getFirst());
        assertEquals(200,owner.get("/api/campus/summaries?buildingId="+building+"&floorId="+floor).statusCode());
        for(String query:List.of("?userId=1","?floorId=0","?floorId="+floor+"&floorId="+floor,"?buildingId=9223372036854775808")) assertEquals(400,owner.get("/api/campus/summaries"+query).statusCode());
        for(String query:List.of("?page=0","?pageSize=101","?category=NONE","?userId=1","?keyword=other")) assertEquals(400,owner.get("/api/campus/locations/"+locationA+"/items"+query).statusCode());
    }
    @Test void unqualified_technician_and_empty_locations_reveal_no_business() throws Exception {
        var owner=login(account(CurrentUser.Role.REPORTER,null)); create(owner,locationA);
        for(var status:List.of(CurrentUser.ReviewStatus.PENDING,CurrentUser.ReviewStatus.REJECTED)) {
            var b=login(account(CurrentUser.Role.TECHNICIAN,status)); assertEquals(200,b.get("/api/campus/catalog").statusCode()); assertEquals(0,summaries(b).get("locations").size());
            var empty=items(b,locationA,""); assertEquals(0,empty.get("visibleTotal").asInt()); assertEquals("NONE",empty.get("category").asString()); assertEquals(0,empty.get("items").get("records").size());
        }
        assertEquals(401,b04Browser().get("/api/campus/summaries").statusCode()); assertEquals(404,owner.get("/api/campus/locations/1/items").statusCode());
    }
    @Test void legacy_and_terminal_requests_do_not_create_markers() throws Exception {
        var owner=login(account(CurrentUser.Role.REPORTER,null)); var manager=login(account(CurrentUser.Role.ADMIN,null));
        long legacy=create(owner,locationA), withdrawn=create(owner,locationB), pending=create(owner,locationA);
        // Isolated compatibility row: the server cannot create LEGACY_TEXT via the public DTO.
        db.update("UPDATE repair_request SET location_mode='LEGACY_TEXT',location_id=NULL,location_name_snapshot=NULL WHERE id=?",legacy);
        assertEquals(200,owner.post("/api/repair-requests/"+withdrawn+"/withdraw",Map.of("version",0,"operationId",op())).statusCode());
        assertEquals(1,summaries(owner).get("locations").size()); assertEquals(1,at(owner,locationA).get("visibleTotal").asInt());
        assertEquals(Long.toString(pending),items(owner,locationA,"").get("items").get("records").get(0).get("id").asString());
        assertEquals(0,items(manager,locationB,"").get("visibleTotal").asInt());
    }
    @Test void concurrent_commit_between_aggregate_and_page_uses_one_repeatable_read_snapshot() throws Exception {
        var owner=login(account(CurrentUser.Role.REPORTER,null)); long request=create(owner,locationA);
        probe.afterAggregate.set(()-> {
            try { assertEquals(200,owner.post("/api/repair-requests/"+request+"/withdraw",Map.of("version",0,"operationId",op())).statusCode()); }
            catch(Exception failure) {throw new AssertionError(failure);}
        });
        var response=items(owner,locationA,"");
        assertEquals("WITHDRAWN",db.queryForObject("SELECT status FROM repair_request WHERE id=?",String.class,request));
        assertEquals(1,response.get("visibleTotal").asInt()); assertEquals(1,response.get("items").get("total").asInt()); assertEquals(1,response.get("items").get("records").size());
        assertEquals(0,items(owner,locationA,"").get("visibleTotal").asInt());
    }
    @Test void query_count_does_not_grow_with_locations_or_items_and_sql_is_read_only_rr() throws Exception {
        var owner=login(account(CurrentUser.Role.REPORTER,null)); create(owner,locationA);
        probe.measuring=true; probe.statements.clear(); summaries(owner); var one=List.copyOf(probe.statements); probe.measuring=false;
        for(int n=0;n<4;n++)create(owner,n%2==0?locationA:locationB);
        probe.measuring=true; probe.statements.clear(); summaries(owner); var many=List.copyOf(probe.statements);
        assertEquals(one,many); assertEquals(2,many.size());
        probe.statements.clear(); items(owner,locationA,""); var page=List.copyOf(probe.statements); probe.measuring=false; assertEquals(4,page.size());
        var evidence=java.nio.file.Path.of("../tests/evidence/T037"); java.nio.file.Files.createDirectories(evidence);
        java.nio.file.Files.writeString(evidence.resolve("sql-snapshot-and-query-count.json"),json.writeValueAsString(Map.of("database","campus_maintenance_test","isolation","REPEATABLE_READ","connectionReadOnly",true,"summariesOneRow",one,"summariesFiveRows",many,"itemsQueries",page,"snapshotConcurrency","separate real HTTP withdrawal committed after aggregate; count/page retained old snapshot; next request saw removal")));
    }
    @Test void successful_supplement_moves_only_its_request_and_duplicate_close_removes_only_its_marker() throws Exception {
        var owner=login(account(CurrentUser.Role.REPORTER,null)); var admin=login(account(CurrentUser.Role.ADMIN,null));
        long moving=create(owner,locationA), staying=create(owner,locationA);
        assertEquals(200,admin.post("/api/repair-requests/"+moving+"/return",Map.of("version",0,"reason","位置需更正","operationId",op())).statusCode());
        assertEquals(2,at(owner,locationA).get("visibleTotal").asInt());
        var body=createBody("",locationB,op()); body.put("attachmentIds",List.of()); body.put("version",1);
        assertEquals(200,owner.post("/api/repair-requests/"+moving+"/supplement",body).statusCode());
        assertEquals(1,at(owner,locationA).get("visibleTotal").asInt()); assertEquals(1,at(owner,locationB).get("visibleTotal").asInt());
        assertEquals(Long.toString(staying),items(owner,locationA,"").get("items").get("records").get(0).get("id").asString());
        long device=new cn.wzbc.maintenance.support.B04WorkOrderFixture(db,time.instant()).equipment(locationB); equipment.add(device);
        assertEquals(200,admin.post("/api/repair-requests/"+moving+"/duplicate-close",Map.of("version",2,"equipmentId",Long.toString(device),"reason","真实重复依据","operationId",op())).statusCode());
        assertEquals(0,items(owner,locationB,"").get("visibleTotal").asInt()); assertEquals(1,at(owner,locationA).get("visibleTotal").asInt());
    }
}
