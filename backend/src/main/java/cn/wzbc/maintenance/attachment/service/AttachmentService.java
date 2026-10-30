package cn.wzbc.maintenance.attachment.service;

import cn.wzbc.maintenance.attachment.*;
import cn.wzbc.maintenance.attachment.entity.AttachmentEntity;
import cn.wzbc.maintenance.attachment.mapper.AttachmentMapper;
import cn.wzbc.maintenance.attachment.vo.AttachmentVo;
import cn.wzbc.maintenance.account.mapper.UserMapper;
import cn.wzbc.maintenance.repairrequest.mapper.RepairRequestMapper;
import cn.wzbc.maintenance.workorder.mapper.WorkOrderMapper;
import cn.wzbc.maintenance.security.*;
import cn.wzbc.maintenance.common.BusinessException;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import java.io.*;
import java.security.MessageDigest;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import javax.imageio.ImageIO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.*;

@Service
public class AttachmentService {
    private final AttachmentMapper files; private final AttachmentStorage storage; private final UserMapper users;
    private final BusinessAccessPolicy access; private final Clock clock; private final TransactionTemplate tx; private final RepairRequestMapper requests; private final WorkOrderMapper orders;
    public AttachmentService(AttachmentMapper files,AttachmentStorage storage,UserMapper users,BusinessAccessPolicy access,Clock clock,PlatformTransactionManager manager,RepairRequestMapper requests,WorkOrderMapper orders) {
        this.files=files; this.storage=storage; this.users=users; this.access=access; this.clock=clock; this.requests=requests; this.orders=orders; tx=new TransactionTemplate(manager);
    }
    public record Upload(String operationId,String purpose,String batchId,Long repairRequestId,Long workOrderId,Integer round) {}
    public record Content(byte[] bytes,String mediaType) {}
    public AttachmentVo upload(CurrentUser actor,Upload input,String name,String mime,byte[] bytes) {
        uuid(input.operationId()); validateContext(actor,input);
        String extension=validateImage(name,mime,bytes); byte[] digest=sha(bytes);
        var previous=files.operation(actor.id(),input.operationId());
        if(previous!=null) return replay(previous,input,name,mime,bytes.length,digest);
        String key;
        try { key=storage.write(bytes,extension); } catch(IOException failure) { throw error(500,"INTERNAL_ERROR","图片保存失败，请重试并核对结果"); }
        try {
            var result=tx.execute(status -> {
                var user=users.lock(actor.id()); if(user==null || !user.enabled) throw error(401,"ACCOUNT_DISABLED","登录身份已失效");
                validateContext(user.identity(),input);
                var repeated=files.operation(actor.id(),input.operationId());
                if(repeated!=null) return repeated;
                var now=clock.instant().truncatedTo(ChronoUnit.MILLIS);
                var file=new AttachmentEntity(); file.id=IdWorker.getId(); file.createdAt=now; file.uploaderId=actor.id(); file.uploadOperationId=input.operationId();
                file.purpose=input.purpose(); file.state="TEMPORARY"; file.batchId=input.batchId(); file.targetRequestId=input.repairRequestId(); file.targetWorkOrderId=input.workOrderId(); file.targetRound=input.round();
                file.originalName=name; file.storageKey=key; file.mediaType=mime; file.byteSize=bytes.length; file.fileDigest=digest; file.uploadedAt=now; file.expiresAt=now.plus(Duration.ofHours(24));
                files.insert(file); return file;
            });
            if(result==null) throw error(500,"INTERNAL_ERROR","附件登记未完成");
            if(!result.storageKey.equals(key)) { compensate(key); return replay(result,input,name,mime,bytes.length,digest); }
            storage.registered(key); return AttachmentVo.from(result);
        } catch(RuntimeException failure) { compensate(key); throw failure; }
    }
    private void compensate(String key) { try { storage.abandon(key); } catch(IOException failure) { throw error(500,"INTERNAL_ERROR","附件登记失败，文件补偿需要重试"); } }
    private AttachmentVo replay(AttachmentEntity file,Upload input,String name,String mime,long size,byte[] digest) {
        if(!Objects.equals(file.purpose,input.purpose()) || !Objects.equals(file.batchId,input.batchId()) || !Objects.equals(file.targetRequestId,input.repairRequestId()) || !Objects.equals(file.targetWorkOrderId,input.workOrderId()) || !Objects.equals(file.targetRound,input.round()) || !file.originalName.equals(name) || !file.mediaType.equals(mime) || file.byteSize!=size || !MessageDigest.isEqual(file.fileDigest,digest)) throw error(409,"OPERATION_CONTENT_MISMATCH","同一上传标识的内容不一致");
        if(!file.state.equals("TEMPORARY") || !clock.instant().isBefore(file.expiresAt)) throw error(409,"ATTACHMENT_EXPIRED","临时附件已失效，请核对后重新选择上传");
        return AttachmentVo.from(file);
    }
    private void validateContext(CurrentUser actor,Upload input) {
        if(actor==null || !actor.enabled()) throw error(401,"AUTH_REQUIRED","请先登录");
        boolean fault="REQUEST_FAULT".equals(input.purpose()), supplement="REQUEST_SUPPLEMENT".equals(input.purpose()), voucher="REPAIR_VOUCHER".equals(input.purpose());
        if(fault || supplement) { if(actor.role()!=CurrentUser.Role.REPORTER) throw error(403,"FORBIDDEN","仅报修用户可上传故障图片"); }
        else if(voucher) {
            if(actor.role()!=CurrentUser.Role.TECHNICIAN || actor.reviewStatus()!=CurrentUser.ReviewStatus.APPROVED) throw error(403,"TECHNICIAN_NOT_APPROVED","当前没有维修业务资格");
            if(input.batchId()!=null || input.repairRequestId()!=null || input.workOrderId()==null || input.round()==null || input.round()<1) throw error(400,"ATTACHMENT_CONTEXT_MISMATCH","维修凭证目标不合法");
            var order=orders.selectById(input.workOrderId());
            if(order==null || order.acceptedTechnicianId==null || order.acceptedTechnicianId!=actor.id()) throw error(403,"RECORD_FORBIDDEN","不是当前实际维修人员");
            if(!order.status.equals("IN_PROGRESS") || order.currentRound!=input.round()) throw error(409,"ROUND_CONFLICT","当前维修轮次已变化");
        } else throw error(400,"ATTACHMENT_CONTEXT_MISMATCH","图片用途不合法");
        if(fault) { uuid(input.batchId()); if(input.repairRequestId()!=null || input.workOrderId()!=null || input.round()!=null) throw error(400,"ATTACHMENT_CONTEXT_MISMATCH","上传目标必须互斥"); }
        if(supplement) {
            if(input.batchId()!=null || input.repairRequestId()==null || input.workOrderId()!=null || input.round()!=null) throw error(400,"ATTACHMENT_CONTEXT_MISMATCH","上传目标必须互斥");
            var request=requests.selectById(input.repairRequestId());
            if(request==null || request.reporterId!=actor.id()) throw error(403,"RECORD_FORBIDDEN","只能补充本人申请");
            if(!request.status.equals("NEEDS_SUPPLEMENT")) throw error(409,"STATE_CONFLICT","申请当前不能补充图片");
        }
    }
    public Content content(CurrentUser actor,long id) {
        var file=files.selectById(id); if(file==null || List.of("DELETING","DELETED").contains(file.state)) throw error(404,"RESOURCE_NOT_FOUND","图片不可用");
        if(!access.canReadAttachment(actor,file)) throw error(403,"RECORD_FORBIDDEN","没有查看该图片的权限");
        try { return new Content(storage.read(file.storageKey),file.mediaType); } catch(IOException failure) { throw error(404,"RESOURCE_NOT_FOUND","图片文件不可用"); }
    }
    public void cancel(CurrentUser actor,long id,String operation) {
        uuid(operation);
        var claimed=tx.execute(status -> {
            var file=files.lock(id); if(file==null) throw error(404,"RESOURCE_NOT_FOUND","临时图片不存在");
            if(actor==null || file.uploaderId!=actor.id()) throw error(403,"RECORD_FORBIDDEN","只能取消本人的临时图片");
            if(file.state.equals("BOUND")) throw error(409,"ATTACHMENT_ALREADY_BOUND","历史图片不能取消");
            if(file.state.equals("TEMPORARY")) {
                files.update(null,new UpdateWrapper<AttachmentEntity>().eq("id",id).eq("state","TEMPORARY").set("state","DELETING").set("deletion_claimed_at",clock.instant().truncatedTo(ChronoUnit.MILLIS)));
            }
            return file;
        });
        if(claimed!=null && !claimed.state.equals("DELETED")) deleteClaimed(claimed.id,claimed.storageKey);
    }
    public boolean deleteClaimed(long id,String key) {
        try { storage.delete(key); }
        catch(IOException failure) { tx.executeWithoutResult(status -> files.update(null,new UpdateWrapper<AttachmentEntity>().eq("id",id).eq("state","DELETING").setSql("delete_attempts=delete_attempts+1"))); return false; }
        tx.executeWithoutResult(status -> files.update(null,new UpdateWrapper<AttachmentEntity>().eq("id",id).eq("state","DELETING").set("state","DELETED").set("deleted_at",clock.instant().truncatedTo(ChronoUnit.MILLIS)).setSql("delete_attempts=delete_attempts+1"))); return true;
    }
    public List<AttachmentVo> bindTemporary(List<Long> ids,AttachmentTarget target,CurrentUser actor) {
        if(!TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("附件绑定必须在业务事务内执行");
        boolean voucher=target.kind().equals("REPAIR_ROUND");
        if(!voucher && (!target.kind().equals("REQUEST_SUBMISSION") || target.round()!=null)) throw error(409,"ATTACHMENT_CONTEXT_MISMATCH","图片目标不合法");
        var submission=voucher?null:requests.submission(target.id());
        if(!voucher && (submission==null || submission.actorUserId!=actor.id())) throw error(403,"RECORD_FORBIDDEN","不能绑定到他人提交");
        var round=voucher?orders.round(target.id()):null;
        if(voucher) {
            if(round==null || target.round()==null || round.roundNo!=target.round()) throw error(409,"ATTACHMENT_CONTEXT_MISMATCH","维修轮次不合法");
            var order=orders.selectById(round.workOrderId);
            if(order==null || !order.status.equals("IN_PROGRESS") || order.currentRound!=round.roundNo || order.acceptedTechnicianId==null || order.acceptedTechnicianId!=actor.id() || round.technicianId!=actor.id() || round.submittedAt!=null) throw error(409,"ROUND_CONFLICT","维修轮次已变化");
        }
        if(ids.size()>5 || ids.stream().distinct().count()!=ids.size()) throw error(400,"ATTACHMENT_COUNT_INVALID","每次最多5张不同图片");
        var now=clock.instant().truncatedTo(ChronoUnit.MILLIS); var bound=new ArrayList<AttachmentVo>(); String batch=null;
        for(long id:ids.stream().sorted().toList()) {
            var file=files.lock(id); if(file==null) throw error(409,"ATTACHMENT_CONTEXT_MISMATCH","临时图片不存在");
            if(file.uploaderId!=actor.id()) throw error(403,"RECORD_FORBIDDEN","不能绑定他人的图片");
            if(!file.state.equals("TEMPORARY")) throw error(409,"ATTACHMENT_ALREADY_BOUND","临时图片已占用或取消");
            if(!now.isBefore(file.expiresAt)) throw error(409,"ATTACHMENT_EXPIRED","临时图片已过期");
            if(voucher) {
                if(!file.purpose.equals("REPAIR_VOUCHER") || !Objects.equals(file.targetWorkOrderId,round.workOrderId) || !Objects.equals(file.targetRound,round.roundNo) || file.targetRequestId!=null || file.batchId!=null) throw error(409,"ATTACHMENT_CONTEXT_MISMATCH","维修凭证不属于本工单本轮次");
            } else if(submission.operationType.equals("CREATE_REQUEST")) {
                if(!file.purpose.equals("REQUEST_FAULT")) throw error(409,"ATTACHMENT_CONTEXT_MISMATCH","首次报修只能绑定本批故障图");
                if(batch==null) batch=file.batchId; else if(!batch.equals(file.batchId)) throw error(409,"ATTACHMENT_CONTEXT_MISMATCH","图片来自不同上传批次");
            } else if(!file.purpose.equals("REQUEST_SUPPLEMENT") || !submission.requestId.equals(file.targetRequestId)) throw error(409,"ATTACHMENT_CONTEXT_MISMATCH","补充图片不属于该申请");
            var update=new UpdateWrapper<AttachmentEntity>().eq("id",file.id).eq("state","TEMPORARY").set("state","BOUND").set("bound_at",now);
            if(voucher) update.set("bound_round_id",target.id()); else update.set("bound_submission_id",target.id());
            if(files.update(null,update)!=1) throw error(409,"ATTACHMENT_ALREADY_BOUND","图片已变化");
            file.state="BOUND"; if(voucher) file.boundRoundId=target.id(); else file.boundSubmissionId=target.id(); file.boundAt=now; bound.add(AttachmentVo.from(file));
        }
        return List.copyOf(bound);
    }
    private static String validateImage(String name,String mime,byte[] bytes) {
        if(bytes.length>5_000_000) throw error(413,"ATTACHMENT_TOO_LARGE","单张图片不能超过5,000,000字节");
        if(bytes.length==0 || name==null || name.length()>255 || name.indexOf('/')>=0 || name.indexOf('\\')>=0 || name.indexOf('\r')>=0 || name.indexOf('\n')>=0 || name.indexOf('\0')>=0) throw error(400,"ATTACHMENT_TYPE_INVALID","图片名称或内容不合法");
        String lower=name.toLowerCase(Locale.ROOT), expected=lower.endsWith(".png")?"png":lower.endsWith(".jpg")||lower.endsWith(".jpeg")?"jpeg":null;
        if(expected==null || !(expected.equals("png")?"image/png":"image/jpeg").equals(mime)) throw error(400,"ATTACHMENT_TYPE_INVALID","仅支持扩展名与类型一致的JPG/JPEG/PNG");
        try(var input=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers=ImageIO.getImageReaders(input); if(!readers.hasNext()) throw new IOException(); var reader=readers.next();
            try { reader.setInput(input,true,true); if(!reader.getFormatName().equalsIgnoreCase(expected) || reader.getWidth(0)<=0 || reader.getHeight(0)<=0 || (long)reader.getWidth(0)*reader.getHeight(0)>40_000_000L || reader.read(0)==null) throw new IOException(); }
            finally { reader.dispose(); }
        } catch(Exception failure) { throw error(400,"ATTACHMENT_TYPE_INVALID","图片实际内容无法解码或与格式不一致"); }
        return expected.equals("png")?"png":"jpg";
    }
    public static void uuid(String value) { if(value==null || !value.matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")) throw error(400,"VALIDATION_FAILED","操作或批次标识不合法"); }
    public static byte[] sha(byte[] bytes) { try { return MessageDigest.getInstance("SHA-256").digest(bytes); } catch(Exception failure) { throw new IllegalStateException(); } }
    public static BusinessException error(int status,String code,String message) { return new BusinessException(HttpStatus.valueOf(status),code,message); }
}
