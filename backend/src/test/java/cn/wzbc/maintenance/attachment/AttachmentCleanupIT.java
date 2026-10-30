package cn.wzbc.maintenance.attachment;
import cn.wzbc.maintenance.security.CurrentUser;
import cn.wzbc.maintenance.support.B04HttpTestSupport;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import cn.wzbc.maintenance.attachment.service.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import static org.junit.jupiter.api.Assertions.*;
class AttachmentCleanupIT extends B04HttpTestSupport {
    @Autowired ApplicationContext context;
    @Autowired AttachmentService attachments;
    @Autowired TemporaryAttachmentCleanup cleanup;
    @Test void bind_and_cleanup_are_mutually_exclusive() throws Exception {
        var reporter=account(CurrentUser.Role.REPORTER,null); var b=b04Browser(); b.login(reporter);
        String first=upload(b); var request=b.post("/api/repair-requests",createBody(first,locationA,UUID.randomUUID().toString()));
        long submission=db.queryForObject("SELECT current_submission_id FROM repair_request WHERE id=?",Long.class,Long.valueOf(data(request).get("resourceId").asString()));
        String temporary=upload(b); var expires=time.instant().plus(Duration.ofHours(24)); var gate=new CountDownLatch(1); AtomicInteger successfulBind=new AtomicInteger();
        try(var pool=Executors.newFixedThreadPool(2)) {
            var bind=pool.submit(() -> { gate.await(); try { new TransactionTemplate(transactions).executeWithoutResult(status -> { attachments.bindTemporary(List.of(Long.valueOf(temporary)),new AttachmentTarget("REQUEST_SUBMISSION",submission,null),reporter.identity()); }); successfulBind.incrementAndGet(); } catch(cn.wzbc.maintenance.common.BusinessException expected) { assertTrue(Set.of("ATTACHMENT_ALREADY_BOUND","ATTACHMENT_EXPIRED").contains(expected.code())); } return 0; });
            var expire=pool.submit(() -> { gate.await(); return cleanup.cleanupExpired(expires).claimed(); }); gate.countDown(); bind.get(15,TimeUnit.SECONDS);
            int successfulBindCount=successfulBind.get(), successfulCleanupClaimCount=expire.get(15,TimeUnit.SECONDS);
            assertEquals(1,successfulBindCount+successfulCleanupClaimCount);
        }
        assertEquals("BOUND",db.queryForObject("SELECT state FROM attachment_file WHERE id=?",String.class,Long.valueOf(first)));
        assertEquals(200,b.content(first).statusCode());
    }
    @Test void registration_and_file_failure_leave_a_known_retryable_orphan_not_an_unknown_sweep() throws Exception {
        var reporter=account(CurrentUser.Role.REPORTER,null); var b=b04Browser(); b.login(reporter); var before=new HashSet<Path>();
        try(var files=Files.list(attachmentRoot)) { files.forEach(before::add); }
        var result=new java.util.concurrent.atomic.AtomicReference<Future<java.net.http.HttpResponse<String>>>();
        var created=new java.util.concurrent.atomic.AtomicReference<Path>(); var blocker=new java.util.concurrent.atomic.AtomicReference<Path>();
        try(var pool=Executors.newSingleThreadExecutor()) {
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                users.lock(reporter.id);
                result.set(pool.submit(() -> b.upload(faultContext(),"fault.png","image/png",png(),true)));
                long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);
                while(created.get()==null && System.nanoTime()<deadline) {
                    try(var paths=Files.list(attachmentRoot)) { paths.filter(p -> !before.contains(p) && p.getFileName().toString().matches("[0-9a-f-]{36}\\.png")).findFirst().ifPresent(created::set); }
                    catch(Exception error) { throw new RuntimeException(error); }
                    if(created.get()==null) java.util.concurrent.locks.LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
                }
                assertNotNull(created.get(),"Real file should be written before SQL registration waits for account lock");
                try { Files.delete(created.get()); Files.createDirectory(created.get()); blocker.set(Files.writeString(created.get().resolve("owned-blocker.txt"),"fixture")); }
                catch(Exception error) { throw new RuntimeException(error); }
                db.update("UPDATE user_account SET enabled=0 WHERE id=?",reporter.id);
            });
            assertEquals(500,result.get().get(15,TimeUnit.SECONDS).statusCode());
            assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM attachment_file WHERE uploader_id=?",Integer.class,reporter.id));
            Files.delete(blocker.get()); Files.delete(created.get()); Files.write(created.get(),png(),StandardOpenOption.CREATE_NEW);
            cleanup.cleanupExpired(time.instant());
            assertFalse(Files.exists(created.get()),"Known failed registration must be retried without sweeping unregistered active/unknown files");
        } finally {
            if(blocker.get()!=null) Files.deleteIfExists(blocker.get());
            if(created.get()!=null) { if(Files.isDirectory(created.get())) Files.delete(created.get()); else Files.deleteIfExists(created.get()); Files.deleteIfExists(attachmentRoot.resolve(created.get().getFileName()+".upload")); }
        }
    }
    /** Before the cleanup exists, no claim occurs; assertions observe real DB/file effects. */
    void clean() throws Exception {
        if(context.containsBean("temporaryAttachmentCleanup")) {
            var service=context.getBean("temporaryAttachmentCleanup"); service.getClass().getMethod("cleanupExpired",java.time.Instant.class).invoke(service,time.instant());
        }
    }
    @Test void twenty_four_hour_boundary_is_exact() throws Exception {
        var b=b04Browser(); b.login(account(CurrentUser.Role.REPORTER,null)); String id=upload(b);
        var start=time.instant(); time.set(start.plus(Duration.ofHours(24)).minusMillis(1)); clean();
        assertEquals("TEMPORARY",db.queryForObject("SELECT state FROM attachment_file WHERE id=?",String.class,Long.valueOf(id)));
        time.set(start.plus(Duration.ofHours(24))); clean();
        assertEquals("DELETED",db.queryForObject("SELECT state FROM attachment_file WHERE id=?",String.class,Long.valueOf(id)));
    }
    @Test void failed_physical_delete_keeps_claim_and_can_retry() throws Exception {
        var b=b04Browser(); b.login(account(CurrentUser.Role.REPORTER,null)); String id=upload(b);
        String key=db.queryForObject("SELECT storage_key FROM attachment_file WHERE id=?",String.class,Long.valueOf(id)); Path file=attachmentRoot.resolve(key);
        Files.delete(file); Files.createDirectory(file); Path blocker=Files.writeString(file.resolve("owned-blocker.txt"),"fixture");
        try {
            time.set(time.instant().plus(Duration.ofHours(24))); clean();
            assertEquals("DELETING",db.queryForObject("SELECT state FROM attachment_file WHERE id=?",String.class,Long.valueOf(id)));
            assertTrue(db.queryForObject("SELECT delete_attempts FROM attachment_file WHERE id=?",Integer.class,Long.valueOf(id))>=1);
        } finally { Files.deleteIfExists(blocker); Files.deleteIfExists(file); Files.write(file,png(),StandardOpenOption.CREATE_NEW); }
        clean(); assertFalse(Files.exists(file));
        assertEquals("DELETED",db.queryForObject("SELECT state FROM attachment_file WHERE id=?",String.class,Long.valueOf(id)));
    }
    @Test void unknown_or_unregistered_file_is_never_swept() throws Exception {
        Path unknown=attachmentRoot.resolve(UUID.randomUUID()+".png"); Files.write(unknown,png(),StandardOpenOption.CREATE_NEW);
        try { time.set(time.instant().plus(Duration.ofDays(2))); clean(); assertTrue(Files.isRegularFile(unknown)); }
        finally { Files.delete(unknown); }
    }
}
