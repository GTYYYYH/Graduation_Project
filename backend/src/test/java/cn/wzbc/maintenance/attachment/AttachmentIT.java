package cn.wzbc.maintenance.attachment;

import cn.wzbc.maintenance.security.CurrentUser;
import cn.wzbc.maintenance.support.B04HttpTestSupport;
import java.nio.file.Files;
import java.util.*;
import cn.wzbc.maintenance.attachment.service.AttachmentService;
import cn.wzbc.maintenance.attachment.AttachmentTarget;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AttachmentIT extends B04HttpTestSupport {
    @Autowired AttachmentService attachments;
    @Test void binding_requires_caller_transaction_and_rollback_preserves_file_and_temp() throws Exception {
        var reporter=account(CurrentUser.Role.REPORTER,null); var b=b04Browser(); b.login(reporter);
        var first=b.post("/api/repair-requests",createBody(upload(b),locationA,UUID.randomUUID().toString())); String request=data(first).get("resourceId").asString();
        long submission=db.queryForObject("SELECT current_submission_id FROM repair_request WHERE id=?",Long.class,Long.valueOf(request)); String image=upload(b);
        String key=db.queryForObject("SELECT storage_key FROM attachment_file WHERE id=?",String.class,Long.valueOf(image));
        assertThrows(IllegalStateException.class,() -> attachments.bindTemporary(List.of(Long.valueOf(image)),new AttachmentTarget("REQUEST_SUBMISSION",submission,null),reporter.identity()));
        new TransactionTemplate(transactions).executeWithoutResult(status -> { attachments.bindTemporary(List.of(Long.valueOf(image)),new AttachmentTarget("REQUEST_SUBMISSION",submission,null),reporter.identity()); status.setRollbackOnly(); });
        assertEquals("TEMPORARY",db.queryForObject("SELECT state FROM attachment_file WHERE id=?",String.class,Long.valueOf(image))); assertArrayEquals(png(),Files.readAllBytes(attachmentRoot.resolve(key)));
        new TransactionTemplate(transactions).executeWithoutResult(status -> attachments.bindTemporary(List.of(Long.valueOf(image)),new AttachmentTarget("REQUEST_SUBMISSION",submission,null),reporter.identity()));
        assertEquals(key,db.queryForObject("SELECT storage_key FROM attachment_file WHERE id=?",String.class,Long.valueOf(image))); assertEquals(200,b.content(image).statusCode());
        assertEquals(409,b.cancel(image,UUID.randomUUID().toString()).statusCode());
    }
    @Test void disguised_image_is_rejected() throws Exception {
        var reporter=account(CurrentUser.Role.REPORTER,null); var b=b04Browser(); b.login(reporter);
        var disguisedImageResponse=b.upload(faultContext(),"fake.png","image/png","not an image".getBytes(),true);
        assertEquals(400,disguisedImageResponse.statusCode());
        assertEquals("ATTACHMENT_TYPE_INVALID",code(disguisedImageResponse));
    }
    @Test void decoded_mime_extension_and_size_are_checked() throws Exception {
        var b=b04Browser(); b.login(account(CurrentUser.Role.REPORTER,null));
        assertEquals(400,b.upload(faultContext(),"fake.jpg","image/jpeg",png(),true).statusCode());
        assertEquals(400,b.upload(faultContext(),"fake.gif","image/png",png(),true).statusCode());
        assertEquals(400,b.upload(faultContext(),"empty.png","image/png",new byte[0],true).statusCode());
        assertEquals(413,b.upload(faultContext(),"large.png","image/png",new byte[5_000_001],true).statusCode());
    }
    @Test void temp_file_is_private_stable_and_owned() throws Exception {
        var b=b04Browser(); b.login(account(CurrentUser.Role.REPORTER,null)); String id=upload(b);
        var r=b.content(id); assertEquals(200,r.statusCode()); assertArrayEquals(png(),r.body());
        assertEquals("private, no-store",r.headers().firstValue("Cache-Control").orElseThrow());
        String key=db.queryForObject("SELECT storage_key FROM attachment_file WHERE id=?",String.class,Long.valueOf(id));
        assertTrue(Files.isRegularFile(attachmentRoot.resolve(key)));
        var other=b04Browser(); other.login(account(CurrentUser.Role.REPORTER,null)); assertEquals(403,other.content(id).statusCode());
        assertEquals(403,other.cancel(id,UUID.randomUUID().toString()).statusCode());
    }
    @Test void upload_and_cancel_require_csrf_and_business_role() throws Exception {
        var b=b04Browser(); b.login(account(CurrentUser.Role.REPORTER,null));
        assertEquals(403,b.upload(faultContext(),"fault.png","image/png",png(),false).statusCode());
        var admin=b04Browser(); admin.login(account(CurrentUser.Role.ADMIN,null));
        assertEquals(403,admin.upload(faultContext(),"fault.png","image/png",png(),true).statusCode());
        var technician=b04Browser(); technician.login(account(CurrentUser.Role.TECHNICIAN,CurrentUser.ReviewStatus.PENDING));
        assertEquals(403,technician.upload(faultContext(),"fault.png","image/png",png(),true).statusCode());
    }
    @Test void upload_retry_is_idempotent_and_other_content_conflicts() throws Exception {
        var b=b04Browser(); b.login(account(CurrentUser.Role.REPORTER,null)); var fields=faultContext();
        var first=b.upload(fields,"fault.png","image/png",png(),true); assertEquals(201,first.statusCode());
        var retry=b.upload(fields,"fault.png","image/png",png(),true); assertEquals(data(first).get("id").asString(),data(retry).get("id").asString());
        var changed=new HashMap<>(fields); changed.put("batchId",UUID.randomUUID().toString());
        assertEquals(409,b.upload(changed,"fault.png","image/png",png(),true).statusCode());
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM attachment_file WHERE uploader_id=?",Integer.class,owned.getFirst()));
    }
    @Test void client_cannot_choose_storage_path_or_mix_contexts() throws Exception {
        var b=b04Browser(); b.login(account(CurrentUser.Role.REPORTER,null));
        var fields=new HashMap<>(faultContext()); fields.put("storageKey","../escape.png");
        assertEquals(400,b.upload(fields,"fault.png","image/png",png(),true).statusCode());
        fields=new HashMap<>(faultContext()); fields.put("repairRequestId","1");
        assertEquals(400,b.upload(fields,"fault.png","image/png",png(),true).statusCode());
        assertEquals(400,b.upload(faultContext(),"../escape.png","image/png",png(),true).statusCode());
    }
    @Test void cancellation_repeats_safely_and_content_disappears() throws Exception {
        var b=b04Browser(); b.login(account(CurrentUser.Role.REPORTER,null)); String id=upload(b);
        String key=db.queryForObject("SELECT storage_key FROM attachment_file WHERE id=?",String.class,Long.valueOf(id));
        String operation=UUID.randomUUID().toString(); assertEquals(200,b.cancel(id,operation).statusCode());
        assertEquals(200,b.cancel(id,operation).statusCode()); assertEquals(404,b.content(id).statusCode());
        assertFalse(Files.exists(attachmentRoot.resolve(key)));
        assertEquals("DELETED",db.queryForObject("SELECT state FROM attachment_file WHERE id=?",String.class,Long.valueOf(id)));
    }
}
