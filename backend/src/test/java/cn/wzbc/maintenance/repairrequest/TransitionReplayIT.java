package cn.wzbc.maintenance.repairrequest;

import cn.wzbc.maintenance.repairrequest.entity.RepairRequestEntity;
import cn.wzbc.maintenance.security.CurrentUser;
import cn.wzbc.maintenance.support.B04HttpTestSupport;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import static org.junit.jupiter.api.Assertions.*;

/** Only the query scheduling is gated; HTTP, locks, SQL, commits and receipts are real. */
@Import(TransitionReplayIT.ReplayConfig.class)
class TransitionReplayIT extends B04HttpTestSupport {
    @Autowired SnapshotGate gate;

    @Test void return_retry_started_before_original_commit_replays_original_receipt() throws Exception { replay("return"); }
    @Test void withdraw_retry_started_before_original_commit_replays_original_receipt() throws Exception { replay("withdraw"); }
    @Test void supplement_retry_started_before_original_commit_replays_original_receipt() throws Exception { replay("supplement"); }

    private void replay(String action) throws Exception {
        var reporter=account(CurrentUser.Role.REPORTER,null); var manager=account(CurrentUser.Role.ADMIN,null);
        var reporting=b04Browser(); reporting.login(reporter); var managing=b04Browser(); managing.login(manager);
        var created=reporting.post("/api/repair-requests",createBody(upload(reporting),locationA,UUID.randomUUID().toString()));
        assertEquals(201,created.statusCode(),created.body()); String id=data(created).get("resourceId").asString();
        int version=0;
        if(action.equals("supplement")) {
            var returned=managing.post("/api/repair-requests/"+id+"/return",Map.of("version",0,"operationId",UUID.randomUUID().toString(),"reason","补充原申请"));
            assertEquals(200,returned.statusCode(),returned.body()); version=1;
        }
        var actor=action.equals("return")?manager:reporter;
        var first=b04Browser(); first.login(actor); var retry=b04Browser(); retry.login(actor);
        String operation=UUID.randomUUID().toString(),path="/api/repair-requests/"+id+"/"+action;
        Map<String,Object> dto=new LinkedHashMap<>(); dto.put("version",version); dto.put("operationId",operation);
        if(action.equals("return")) dto.put("reason","同一原操作的真实重放");
        if(action.equals("supplement")) { dto.putAll(createBody("unused",locationB,operation)); dto.put("attachmentIds",List.of()); dto.put("description","并发补充只保存一次"); }
        gate.arm(Long.parseLong(id));
        HttpResponse<String> original,response;
        try(var pool=Executors.newFixedThreadPool(2)) {
            try {
                var originalFuture=pool.submit(() -> first.post(path,dto));
                assertTrue(gate.firstLocked.await(5,TimeUnit.SECONDS),"原事务须已持有用户和申请锁");
                var retryFuture=pool.submit(() -> retry.post(path,dto));
                assertTrue(gate.secondSnapshot.await(5,TimeUnit.SECONDS),"重试须在原事务提交前完成普通申请读取");
                assertEquals(version,gate.secondVersion.get(),"重试须确实读到原操作前的版本");
                gate.releaseFirst.countDown();
                original=originalFuture.get(10,TimeUnit.SECONDS); response=retryFuture.get(10,TimeUnit.SECONDS);
            } finally { gate.releaseFirst.countDown(); gate.disarm(); }
        }
        assertEquals(200,original.statusCode(),original.body());
        assertEquals(200,response.statusCode(),response.body()); assertEquals(data(original),data(response));
        String type=action.equals("return")?"RETURN_REQUEST":action.equals("withdraw")?"WITHDRAW_REQUEST":"SUPPLEMENT_REQUEST";
        String table=action.equals("supplement")?"repair_request_submission":"repair_request_event";
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE request_id=? AND actor_user_id=? AND operation_type=? AND operation_id=?",Integer.class,Long.parseLong(id),actor.id,type,operation));
        assertEquals(version+1,db.queryForObject("SELECT version FROM repair_request WHERE id=?",Integer.class,Long.parseLong(id)));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM work_order WHERE request_id=?",Integer.class,Long.parseLong(id)));
        assertEquals(action.equals("supplement")?2:1,db.queryForObject("SELECT COUNT(*) FROM repair_request_submission WHERE request_id=?",Integer.class,Long.parseLong(id)));
        assertEquals(data(original),data(first.get("/api/operations/"+operation+"?operation="+type)).get("receipt"));
    }

    @TestConfiguration(proxyBeanMethods=false)
    static class ReplayConfig { @Bean SnapshotGate replaySnapshotGate() { return new SnapshotGate(); } }

    @Intercepts(@Signature(type=Executor.class,method="query",args={MappedStatement.class,Object.class,RowBounds.class,ResultHandler.class}))
    static final class SnapshotGate implements Interceptor {
        private volatile Long target;
        private final AtomicInteger reads=new AtomicInteger(); private final AtomicBoolean locked=new AtomicBoolean();
        volatile CountDownLatch firstLocked,secondSnapshot,releaseFirst;
        final AtomicInteger secondVersion=new AtomicInteger(-1);
        void arm(long request) { reads.set(0); locked.set(false); secondVersion.set(-1); firstLocked=new CountDownLatch(1); secondSnapshot=new CountDownLatch(1); releaseFirst=new CountDownLatch(1); target=request; }
        void disarm() { target=null; }
        @Override public Object intercept(Invocation invocation) throws Throwable {
            Object result=invocation.proceed(); Long own=target;
            if(own==null || !(result instanceof List<?> rows) || rows.isEmpty() || !(rows.getFirst() instanceof RepairRequestEntity row) || !own.equals(row.id)) return result;
            String statement=((MappedStatement)invocation.getArgs()[0]).getId();
            if(statement.endsWith("RepairRequestMapper.selectById") && reads.incrementAndGet()==2) { secondVersion.set(row.version); secondSnapshot.countDown(); }
            if(statement.endsWith("RepairRequestMapper.lock") && locked.compareAndSet(false,true)) {
                firstLocked.countDown(); if(!releaseFirst.await(7,TimeUnit.SECONDS)) throw new IllegalStateException("未及时释放本测试事务门闩");
            }
            return result;
        }
    }
}
