package cn.wzbc.maintenance.integration;

import cn.wzbc.maintenance.security.CurrentUser;
import cn.wzbc.maintenance.support.B04HttpTestSupport;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import cn.wzbc.maintenance.repairrequest.entity.RepairRequestEntity;
import static org.junit.jupiter.api.Assertions.*;

@Import(ConcurrencyIT.GateConfig.class)
class ConcurrencyIT extends B04HttpTestSupport {
    @Autowired TransactionGate transactionGate;
    @Autowired SharedGate sharedGate;
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"false,false", "true,false", "false,true", "true,true"})
    void only_one_conflicting_transition_commits(boolean approvalFirst, boolean returnInstead) throws Exception {
        var reporter = account(CurrentUser.Role.REPORTER, null);
        var admin = account(CurrentUser.Role.ADMIN, null);
        var technician = account(CurrentUser.Role.TECHNICIAN, CurrentUser.ReviewStatus.APPROVED);
        var owner = b04Browser(); owner.login(reporter);
        var manager = b04Browser(); manager.login(admin);
        var returning = b04Browser(); returning.login(admin);
        var created = owner.post("/api/repair-requests", createBody(upload(owner), locationA, UUID.randomUUID().toString()));
        assertEquals(201, created.statusCode(), created.body());
        long request = Long.parseLong(data(created).get("resourceId").asString());
        transactionGate.arm(request);
        try (var pool = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<java.net.http.HttpResponse<String>> withdrawAction = () -> returnInstead ? returning.post("/api/repair-requests/" + request + "/return", Map.of("version", 0, "reason", "本次范围外请补充", "operationId", UUID.randomUUID().toString())) : owner.post("/api/repair-requests/" + request + "/withdraw", Map.of("version", 0, "operationId", UUID.randomUUID().toString()));
            java.util.concurrent.Callable<java.net.http.HttpResponse<String>> approveAction = () -> manager.post("/api/repair-requests/" + request + "/approve-and-assign", Map.of("version", 0, "technicianId", technician.id.toString(), "confirmPreviouslyRefused", false, "operationId", UUID.randomUUID().toString()));
            var first = pool.submit(approvalFirst ? approveAction : withdrawAction);
            assertTrue(transactionGate.locked.await(5, TimeUnit.SECONDS));
            var second = pool.submit(approvalFirst ? withdrawAction : approveAction);
            try {
                assertTrue(transactionGate.secondRead.await(5, TimeUnit.SECONDS));
                assertNotEquals(transactionGate.firstConnection, transactionGate.secondConnection);
                assertEquals(0, transactionGate.secondVersion);
            } finally { transactionGate.release.countDown(); }
            var w = (approvalFirst ? second : first).get(20, TimeUnit.SECONDS);
            var a = (approvalFirst ? first : second).get(20, TimeUnit.SECONDS);
            int successfulConflictingTransitionCount = (w.statusCode() == 200 ? 1 : 0) + (a.statusCode() == 200 ? 1 : 0);
            assertEquals(1, successfulConflictingTransitionCount, "withdraw=" + w.statusCode() + ", approve=" + a.statusCode());
            assertTrue(w.statusCode() == 200 || w.statusCode() == 409, w.body());
            assertTrue(a.statusCode() == 200 || a.statusCode() == 409, a.body());
            int orders = db.queryForObject("SELECT COUNT(*) FROM work_order WHERE request_id=?", Integer.class, request);
            assertEquals(a.statusCode() == 200 ? 1 : 0, orders);
            assertEquals(w.statusCode() == 200 ? (returnInstead ? "NEEDS_SUPPLEMENT" : "WITHDRAWN") : "APPROVED", db.queryForObject("SELECT status FROM repair_request WHERE id=?", String.class, request));
            assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM repair_request_event WHERE request_id=? AND event_type IN ('WITHDRAWN','RETURNED','APPROVED_ASSIGNED')", Integer.class, request));
            assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM repair_request_submission WHERE request_id=?", Integer.class, request));
            assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM attachment_file WHERE bound_submission_id IN (SELECT id FROM repair_request_submission WHERE request_id=?)", Integer.class, request));
            assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM repair_round WHERE work_order_id IN (SELECT id FROM work_order WHERE request_id=?)", Integer.class, request));
            java.nio.file.Files.writeString(java.nio.file.Path.of("../tests/evidence/T060/withdraw-approve-transactions-" + approvalFirst + "-return-" + returnInstead + ".json"), json.writeValueAsString(Map.of("firstConnection", transactionGate.firstConnection, "secondConnection", transactionGate.secondConnection, "secondReadVersion", transactionGate.secondVersion, "firstAutoCommit", transactionGate.firstAutoCommit, "secondAutoCommit", transactionGate.secondAutoCommit, "withdrawHttp", w.statusCode(), "approveHttp", a.statusCode(), "orders", orders)));
        } finally { transactionGate.release.countDown(); transactionGate.target = null; }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
    void accept_and_reassign_commit_only_one_branch(boolean assignmentFirst) throws Exception {
        var reporter=account(CurrentUser.Role.REPORTER,null);
        var admin=account(CurrentUser.Role.ADMIN,null);
        var current=account(CurrentUser.Role.TECHNICIAN,CurrentUser.ReviewStatus.APPROVED);
        var next=account(CurrentUser.Role.TECHNICIAN,CurrentUser.ReviewStatus.APPROVED);
        var owner=b04Browser(); owner.login(reporter);
        var manager=b04Browser(); manager.login(admin);
        var worker=b04Browser(); worker.login(current);
        long request=Long.parseLong(data(owner.post("/api/repair-requests",createBody(upload(owner),locationA,UUID.randomUUID().toString()))).get("resourceId").asString());
        var approval=manager.post("/api/repair-requests/"+request+"/approve-and-assign",Map.of("version",0,"technicianId",current.id.toString(),"confirmPreviouslyRefused",false,"operationId",UUID.randomUUID().toString()));
        assertEquals(200,approval.statusCode(),approval.body());
        long order=Long.parseLong(data(approval).get("relatedResourceId").asString());
        java.util.concurrent.Callable<java.net.http.HttpResponse<String>> accept=()->worker.post("/api/work-orders/"+order+"/accept",Map.of("version",0,"operationId",UUID.randomUUID().toString()));
        java.util.concurrent.Callable<java.net.http.HttpResponse<String>> assign=()->manager.post("/api/work-orders/"+order+"/assign",Map.of("version",0,"technicianId",next.id.toString(),"reason","本次协调改派","confirmPreviouslyRefused",false,"operationId",UUID.randomUUID().toString()));
        transactionGate.arm(order);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var first=pool.submit(assignmentFirst?assign:accept);
            assertTrue(transactionGate.locked.await(5,TimeUnit.SECONDS));
            var second=pool.submit(assignmentFirst?accept:assign);
            try { assertTrue(transactionGate.secondRead.await(5,TimeUnit.SECONDS)); assertNotEquals(transactionGate.firstConnection,transactionGate.secondConnection); assertEquals(0,transactionGate.secondVersion); }
            finally { transactionGate.release.countDown(); }
            var winner=first.get(15,TimeUnit.SECONDS); var loser=second.get(15,TimeUnit.SECONDS);
            assertEquals(200,winner.statusCode(),winner.body());
            assertEquals(409,loser.statusCode(),loser.body());
            assertEquals(assignmentFirst?"STATE_CONFLICT":"VERSION_CONFLICT",code(loser));
            assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM work_order_event WHERE work_order_id=? AND event_type IN ('ACCEPTED','ASSIGNED') AND operation_id IS NOT NULL",Integer.class,order));
            assertEquals(assignmentFirst?2:1,db.queryForObject("SELECT COUNT(*) FROM work_order_event WHERE work_order_id=? AND event_type IN ('ACCEPTED','ASSIGNED')",Integer.class,order));
            assertEquals(assignmentFirst?0:1,db.queryForObject("SELECT COUNT(*) FROM repair_round WHERE work_order_id=?",Integer.class,order));
            assertEquals(assignmentFirst?2:1,db.queryForObject("SELECT COUNT(*) FROM work_order_assignment WHERE work_order_id=?",Integer.class,order));
            assertEquals(assignmentFirst?"PENDING_ACCEPT":"IN_PROGRESS",db.queryForObject("SELECT status FROM work_order WHERE id=?",String.class,order));
            java.nio.file.Files.writeString(java.nio.file.Path.of("../tests/evidence/T060/accept-assign-"+assignmentFirst+".json"),json.writeValueAsString(Map.of("firstConnection",transactionGate.firstConnection,"secondConnection",transactionGate.secondConnection,"winnerHttp",winner.statusCode(),"loserHttp",loser.statusCode())));
        } finally { transactionGate.release.countDown(); transactionGate.target=null; }
    }

    private java.util.List<java.net.http.HttpResponse<String>> sharedRace(String name, String mapper, java.util.concurrent.Callable<java.net.http.HttpResponse<String>> firstAction, java.util.concurrent.Callable<java.net.http.HttpResponse<String>> secondAction) throws Exception {
        sharedGate.arm(mapper);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var first=pool.submit(firstAction);
            assertTrue(sharedGate.locked.await(5,TimeUnit.SECONDS),"first transaction holds lock");
            var second=pool.submit(secondAction);
            try { assertTrue(sharedGate.attempt.await(5,TimeUnit.SECONDS),"second transaction attempts common lock"); assertNotEquals(sharedGate.firstConnection,sharedGate.secondConnection); }
            finally { sharedGate.release.countDown(); }
            var responses=java.util.List.of(first.get(15,TimeUnit.SECONDS),second.get(15,TimeUnit.SECONDS));
            java.nio.file.Files.writeString(java.nio.file.Path.of("../tests/evidence/T060/"+name+".json"),json.writeValueAsString(Map.of("firstConnection",sharedGate.firstConnection,"secondConnection",sharedGate.secondConnection,"statuses",responses.stream().map(java.net.http.HttpResponse::statusCode).toList(),"codes",responses.stream().map(this::code).toList())));
            return responses;
        } finally { sharedGate.release.countDown(); sharedGate.mapper=null; }
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
    void disable_and_create_recheck_after_common_user_lock(boolean disableFirst) throws Exception {
        var admin=account(CurrentUser.Role.ADMIN,null); var reporter=account(CurrentUser.Role.REPORTER,null);
        var manager=b04Browser();manager.login(admin);var owner=b04Browser();owner.login(reporter);
        var body=createBody(upload(owner),locationA,UUID.randomUUID().toString());
        java.util.concurrent.Callable<java.net.http.HttpResponse<String>> create=()->owner.post("/api/repair-requests",body);
        java.util.concurrent.Callable<java.net.http.HttpResponse<String>> disable=()->manager.post("/api/admin/accounts/"+reporter.id+"/disable",Map.of("version",0,"operationId",UUID.randomUUID().toString()));
        var r=sharedRace("disable-create-"+disableFirst,"UserMapper",disableFirst?disable:create,disableFirst?create:disable);
        assertEquals(disableFirst?200:201,r.get(0).statusCode(),r.get(0).body());
        assertEquals(disableFirst?401:409,r.get(1).statusCode(),r.get(1).body());
        assertEquals(!disableFirst,users.selectById(reporter.id).enabled);
        assertEquals(disableFirst?0:1,db.queryForObject("SELECT COUNT(*) FROM repair_request WHERE reporter_id=?",Integer.class,reporter.id));
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
    void password_change_wins_before_old_password_login(boolean loginFirst) throws Exception {
        var reporter=account(CurrentUser.Role.REPORTER,null);var owner=b04Browser();owner.login(reporter);var login=b04Browser();login.csrf();
        java.util.concurrent.Callable<java.net.http.HttpResponse<String>> change=()->owner.post("/api/account/password",Map.of("version",0,"operationId",UUID.randomUUID().toString(),"currentPassword",PASSWORD,"newPassword","NewSynthetic2!","confirmNewPassword","NewSynthetic2!"));
        java.util.concurrent.Callable<java.net.http.HttpResponse<String>> signing=()->login.post("/api/auth/login",Map.of("loginName",reporter.loginName,"password",PASSWORD));
        var r=sharedRace("password-login-"+loginFirst,"UserMapper",loginFirst?signing:change,loginFirst?change:signing);
        assertEquals(200,r.get(0).statusCode(),r.get(0).body());assertEquals(loginFirst?200:401,r.get(1).statusCode(),r.get(1).body());
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM login_session WHERE user_id=? AND revoked_at IS NULL",Integer.class,reporter.id));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM account_event WHERE target_user_id=? AND event_type='PASSWORD_CHANGED'",Integer.class,reporter.id));
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
    void technician_disable_and_assignment_recheck_common_user_lock(boolean disableFirst) throws Exception {
        var reporter=account(CurrentUser.Role.REPORTER,null);var admin=account(CurrentUser.Role.ADMIN,null);var maintainer=account(CurrentUser.Role.ADMIN,null);var tech=account(CurrentUser.Role.TECHNICIAN,CurrentUser.ReviewStatus.APPROVED);
        var owner=b04Browser();owner.login(reporter);var manager=b04Browser();manager.login(admin);var maintenance=b04Browser();maintenance.login(maintainer);
        long req=Long.parseLong(data(owner.post("/api/repair-requests",createBody(upload(owner),locationA,UUID.randomUUID().toString()))).get("resourceId").asString());
        java.util.concurrent.Callable<java.net.http.HttpResponse<String>> approve=()->manager.post("/api/repair-requests/"+req+"/approve-and-assign",Map.of("version",0,"technicianId",tech.id.toString(),"confirmPreviouslyRefused",false,"operationId",UUID.randomUUID().toString()));
        java.util.concurrent.Callable<java.net.http.HttpResponse<String>> disable=()->maintenance.post("/api/admin/accounts/"+tech.id+"/disable",Map.of("version",0,"operationId",UUID.randomUUID().toString()));
        var r=sharedRace("technician-disable-assign-"+disableFirst,"UserMapper",disableFirst?disable:approve,disableFirst?approve:disable);
        assertEquals(200,r.get(0).statusCode(),r.get(0).body());assertEquals(409,r.get(1).statusCode(),r.get(1).body());
        assertEquals(disableFirst?0:1,db.queryForObject("SELECT COUNT(*) FROM work_order WHERE request_id=?",Integer.class,req));assertEquals(!disableFirst,users.selectById(tech.id).enabled);
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"false,false","false,true","true,false","true,true"})
    void equipment_review_rechecks_shared_lock(boolean approvalFirst,boolean move) throws Exception {
        var reporter=account(CurrentUser.Role.REPORTER,null);var admin=account(CurrentUser.Role.ADMIN,null);var maintainer=account(CurrentUser.Role.ADMIN,null);var tech=account(CurrentUser.Role.TECHNICIAN,CurrentUser.ReviewStatus.APPROVED);
        var owner=b04Browser();owner.login(reporter);var manager=b04Browser();manager.login(admin);var maintenance=b04Browser();maintenance.login(maintainer);
        long eq=new cn.wzbc.maintenance.support.B04WorkOrderFixture(db,time.instant()).equipment(locationA);equipment.add(eq);
        long req=Long.parseLong(data(owner.post("/api/repair-requests",createBody(upload(owner),locationA,UUID.randomUUID().toString()))).get("resourceId").asString());
        java.util.concurrent.Callable<java.net.http.HttpResponse<String>> approve=()->manager.post("/api/repair-requests/"+req+"/approve-and-assign",Map.of("version",0,"technicianId",tech.id.toString(),"equipmentId",Long.toString(eq),"confirmPreviouslyRefused",false,"operationId",UUID.randomUUID().toString()));
        java.util.concurrent.Callable<java.net.http.HttpResponse<String>> mutate=()->move?maintenance.request("PATCH","/api/equipment/"+eq,Map.of("version",0,"name","演示空调","locationId",Long.toString(locationB),"addressText","隔离移动","facilityType","AIR_CONDITIONER","reason","本次移动","operationId",UUID.randomUUID().toString()),maintenance.csrf):maintenance.post("/api/equipment/"+eq+"/disable",Map.of("version",0,"operationId",UUID.randomUUID().toString()));
        var r=sharedRace("equipment-review-"+approvalFirst+"-move-"+move,"EquipmentMapper",approvalFirst?approve:mutate,approvalFirst?mutate:approve);
        assertEquals(200,r.get(0).statusCode(),r.get(0).body());
        assertEquals(approvalFirst&&!move?200:409,r.get(1).statusCode(),r.get(1).body());
        assertEquals(approvalFirst?1:0,db.queryForObject("SELECT COUNT(*) FROM work_order WHERE request_id=?",Integer.class,req));
        assertEquals(move&&!approvalFirst?locationB:locationA,db.queryForObject("SELECT location_id FROM equipment WHERE id=?",Long.class,eq));
        assertEquals(move,db.queryForObject("SELECT enabled FROM equipment WHERE id=?",Boolean.class,eq));
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
    void binding_and_cleanup_recheck_attachment_lock(boolean cleanupFirst) throws Exception {
        var reporter=account(CurrentUser.Role.REPORTER,null);var owner=b04Browser();owner.login(reporter);String image=upload(owner);
        var body=createBody(image,locationA,UUID.randomUUID().toString());
        var cleanup=contextCleanup();sharedGate.arm("AttachmentMapper");
        try(var pool=Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Object> bind=()->owner.post("/api/repair-requests",body);
            java.util.concurrent.Callable<Object> clean=()->cleanup.cleanupExpired(time.instant().plus(java.time.Duration.ofHours(24)));
            var first=pool.submit(cleanupFirst?clean:bind);assertTrue(sharedGate.locked.await(5,TimeUnit.SECONDS));
            var second=pool.submit(cleanupFirst?bind:clean);
            try {assertTrue(sharedGate.attempt.await(5,TimeUnit.SECONDS));assertNotEquals(sharedGate.firstConnection,sharedGate.secondConnection);}finally{sharedGate.release.countDown();}
            Object a=first.get(15,TimeUnit.SECONDS),b=second.get(15,TimeUnit.SECONDS);
            @SuppressWarnings("unchecked") var response=(java.net.http.HttpResponse<String>)(cleanupFirst?b:a);
            var cleared=(cn.wzbc.maintenance.attachment.service.TemporaryAttachmentCleanup.CleanupResult)(cleanupFirst?a:b);
            assertEquals(cleanupFirst?409:201,response.statusCode(),response.body());assertEquals(cleanupFirst?1:0,cleared.claimed());
            assertEquals(cleanupFirst?"DELETED":"BOUND",db.queryForObject("SELECT state FROM attachment_file WHERE id=?",String.class,Long.parseLong(image)));
            assertEquals(cleanupFirst?0:1,db.queryForObject("SELECT COUNT(*) FROM repair_request WHERE reporter_id=?",Integer.class,reporter.id));
            java.nio.file.Files.writeString(java.nio.file.Path.of("../tests/evidence/T060/bind-cleanup-"+cleanupFirst+".json"),json.writeValueAsString(Map.of("firstConnection",sharedGate.firstConnection,"secondConnection",sharedGate.secondConnection,"bindHttp",response.statusCode(),"claimed",cleared.claimed())));
        }finally{sharedGate.release.countDown();sharedGate.mapper=null;}
    }
    @Autowired cn.wzbc.maintenance.attachment.service.TemporaryAttachmentCleanup cleanupService;
    private cn.wzbc.maintenance.attachment.service.TemporaryAttachmentCleanup contextCleanup(){return cleanupService;}

    @Intercepts(@Signature(type=Executor.class,method="query",args={MappedStatement.class,Object.class,RowBounds.class,ResultHandler.class}))
    static final class SharedGate implements Interceptor {
        volatile String mapper; volatile long firstThread,firstConnection,secondConnection;
        volatile CountDownLatch locked,attempt,release;
        void arm(String name) {mapper=name;firstThread=0;firstConnection=0;secondConnection=0;locked=new CountDownLatch(1);attempt=new CountDownLatch(1);release=new CountDownLatch(1);}
        @Override public Object intercept(Invocation call) throws Throwable {
            String name=((MappedStatement)call.getArgs()[0]).getId();
            if(mapper==null || !(name.endsWith(mapper+".lock")||name.endsWith(mapper+".lockOrdered"))) return call.proceed();
            var connection=((Executor)call.getTarget()).getTransaction().getConnection(); assertFalse(connection.getAutoCommit());
            long id;try(var st=connection.createStatement();var rs=st.executeQuery("SELECT CONNECTION_ID()")){rs.next();id=rs.getLong(1);}
            long thread=Thread.currentThread().threadId();
            if(firstThread!=0 && thread!=firstThread) {secondConnection=id;attempt.countDown();return call.proceed();}
            Object result=call.proceed();
            if(firstThread==0) {firstThread=thread;firstConnection=id;locked.countDown();if(!release.await(8,TimeUnit.SECONDS))throw new IllegalStateException("shared lock test release timeout");}
            return result;
        }
    }

    @TestConfiguration(proxyBeanMethods=false)
    static class GateConfig { @Bean TransactionGate b11TransactionGate() { return new TransactionGate(); } @Bean SharedGate b11SharedGate() { return new SharedGate(); } }

    @Intercepts(@Signature(type=Executor.class, method="query", args={MappedStatement.class,Object.class,RowBounds.class,ResultHandler.class}))
    static final class TransactionGate implements Interceptor {
        volatile Long target;
        volatile CountDownLatch locked, secondRead, release;
        volatile long firstConnection, secondConnection;
        volatile boolean firstAutoCommit, secondAutoCommit;
        volatile int secondVersion;
        void arm(long id) { target=id; firstConnection=0; secondConnection=0; locked=new CountDownLatch(1); secondRead=new CountDownLatch(1); release=new CountDownLatch(1); }
        @Override public Object intercept(Invocation invocation) throws Throwable {
            Object result=invocation.proceed();
            if(target==null || !(result instanceof java.util.List<?> rows) || rows.isEmpty()) return result;
            Long rowId; int version;
            if(rows.getFirst() instanceof RepairRequestEntity row) { rowId=row.id; version=row.version; }
            else if(rows.getFirst() instanceof cn.wzbc.maintenance.workorder.entity.WorkOrderEntity row) { rowId=row.id; version=row.version; }
            else return result;
            if(!target.equals(rowId)) return result;
            String name=((MappedStatement)invocation.getArgs()[0]).getId();
            var connection=((Executor)invocation.getTarget()).getTransaction().getConnection();
            long id;
            try(var statement=connection.createStatement(); var rs=statement.executeQuery("SELECT CONNECTION_ID()")) { rs.next(); id=rs.getLong(1); }
            assertFalse(connection.getAutoCommit(), "业务查询必须在真实事务中");
            if(name.endsWith("Mapper.lock") && firstConnection==0) {
                firstConnection=id; firstAutoCommit=connection.getAutoCommit(); locked.countDown();
                if(!release.await(8, TimeUnit.SECONDS)) throw new IllegalStateException("测试事务门闩未释放");
            } else if(name.endsWith("Mapper.selectById") && firstConnection!=0 && id!=firstConnection) {
                secondConnection=id; secondAutoCommit=connection.getAutoCommit(); secondVersion=version; secondRead.countDown();
            }
            return result;
        }
    }
}
