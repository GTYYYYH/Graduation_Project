package cn.wzbc.maintenance.attachment.service;
import cn.wzbc.maintenance.attachment.entity.AttachmentEntity;
import cn.wzbc.maintenance.attachment.mapper.AttachmentMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import java.time.*;
import java.time.temporal.ChronoUnit;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
@Component
@EnableScheduling
public class TemporaryAttachmentCleanup {
    private final AttachmentMapper files; private final AttachmentService service; private final AttachmentStorage storage; private final TransactionTemplate tx; private final Clock clock; private final boolean scheduled;
    public TemporaryAttachmentCleanup(AttachmentMapper files,AttachmentService service,AttachmentStorage storage,PlatformTransactionManager manager,Clock clock,Environment env) {
        this.files=files; this.service=service; this.storage=storage; this.clock=clock; tx=new TransactionTemplate(manager); scheduled=env.getProperty("campus.attachment-cleanup.enabled",Boolean.class,true);
    }
    public record CleanupResult(int claimed,int deleted,int retryPending) {}
    public CleanupResult cleanupExpired(Instant now) {
        var candidates=files.selectList(new QueryWrapper<AttachmentEntity>().and(q -> q.eq("state","DELETING").or(p -> p.eq("state","TEMPORARY").le("expires_at",now))).orderByAsc("id").last("LIMIT 1000"));
        int claimed=0,deleted=0,retry=0;
        for(var candidate:candidates) {
            var result=tx.execute(status -> {
                var file=files.lock(candidate.id); if(file==null || file.state.equals("BOUND") || file.state.equals("DELETED")) return null;
                boolean fresh=file.state.equals("TEMPORARY"); if(fresh && now.isBefore(file.expiresAt)) return null;
                if(fresh) files.update(null,new UpdateWrapper<AttachmentEntity>().eq("id",file.id).eq("state","TEMPORARY").set("state","DELETING").set("deletion_claimed_at",now.truncatedTo(ChronoUnit.MILLIS)));
                return new Claim(file.id,file.storageKey,fresh);
            });
            if(result!=null) { if(result.fresh()) claimed++; if(service.deleteClaimed(result.id(),result.key())) deleted++; else retry++; }
        }
        var recovery=storage.recoverInterrupted(key -> files.selectCount(new QueryWrapper<AttachmentEntity>().eq("storage_key",key))>0);
        return new CleanupResult(claimed,deleted+recovery.deleted(),retry+recovery.retryPending());
    }
    private record Claim(long id,String key,boolean fresh) {}
    @Scheduled(initialDelayString="${campus.attachment-cleanup.interval-ms:60000}",fixedDelayString="${campus.attachment-cleanup.interval-ms:60000}")
    public void scheduledCleanup() { if(scheduled) cleanupExpired(clock.instant()); }
}
