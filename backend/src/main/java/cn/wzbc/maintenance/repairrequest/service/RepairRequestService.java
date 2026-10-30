package cn.wzbc.maintenance.repairrequest.service;

import cn.wzbc.maintenance.repairrequest.dto.*;
import cn.wzbc.maintenance.repairrequest.entity.*;
import cn.wzbc.maintenance.repairrequest.mapper.RepairRequestMapper;
import cn.wzbc.maintenance.repairrequest.vo.RepairRequestDetailVo;
import cn.wzbc.maintenance.repairrequest.vo.RepairRequestDetailVo.*;
import cn.wzbc.maintenance.attachment.AttachmentTarget;
import cn.wzbc.maintenance.attachment.entity.AttachmentEntity;
import cn.wzbc.maintenance.attachment.mapper.AttachmentMapper;
import cn.wzbc.maintenance.attachment.service.AttachmentService;
import cn.wzbc.maintenance.attachment.vo.AttachmentVo;
import cn.wzbc.maintenance.account.mapper.UserMapper;
import cn.wzbc.maintenance.account.vo.CurrentUserVo;
import cn.wzbc.maintenance.equipment.mapper.EquipmentMapper;
import cn.wzbc.maintenance.workorder.mapper.WorkOrderMapper;
import cn.wzbc.maintenance.common.*;
import cn.wzbc.maintenance.security.*;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import static cn.wzbc.maintenance.common.OperationResultService.*;

@Service
public class RepairRequestService {
    private final RepairRequestMapper requests; private final UserMapper users; private final EquipmentMapper equipment; private final AttachmentMapper files;
    private final WorkOrderMapper orders;
    private final AttachmentService attachments; private final BusinessAccessPolicy access; private final OperationResultService operations; private final Clock clock;
    public RepairRequestService(RepairRequestMapper requests,UserMapper users,EquipmentMapper equipment,AttachmentMapper files,AttachmentService attachments,BusinessAccessPolicy access,OperationResultService operations,Clock clock,WorkOrderMapper orders) {
        this.requests=requests; this.users=users; this.equipment=equipment; this.files=files; this.attachments=attachments; this.access=access; this.operations=operations; this.clock=clock; this.orders=orders;
    }
    @Transactional
    public Receipt create(CurrentUser actor,CreateRepairRequestDto dto) {
        access.requireUser(actor,Set.of(CurrentUser.Role.REPORTER),true); uuid(dto.operationId());
        var ids=attachmentIds(dto.attachmentIds()); long location=id(dto.locationId());
        var fields=fields(dto.equipmentName(),dto.equipmentCategory(),dto.addressText(),dto.description(),dto.contactName(),dto.contactPhone());
        byte[] digest=operations.digest(Arrays.asList("CREATE_REQUEST",null,null,null,location,fields,ids));
        var replay=operations.replay(actor,"CREATE_REQUEST",dto.operationId(),digest); if(replay!=null) return replay;
        if(ids.isEmpty()) throw error(400,"ATTACHMENT_COUNT_INVALID","首次报修至少需要一张故障图");
        String snapshot=reportableLocation(location); Instant now=clock.instant().truncatedTo(ChronoUnit.MILLIS);
        var request=new RepairRequestEntity(); request.id=IdWorker.getId(); request.createdAt=now; request.updatedAt=now; request.requestNo="RR-"+request.id; request.reporterId=actor.id(); request.status="PENDING_REVIEW"; request.firstSubmittedAt=now;
        apply(request,fields); request.locationMode="STRUCTURED"; request.locationId=location; request.locationNameSnapshot=snapshot;
        requests.insert(request); var submission=saveSubmission(request,actor,1,"CREATE_REQUEST",dto.operationId(),digest,now);
        requests.update(null,new UpdateWrapper<RepairRequestEntity>().eq("id",request.id).set("current_submission_id",submission.id)); request.currentSubmissionId=submission.id;
        attachments.bindTemporary(ids,new AttachmentTarget("REQUEST_SUBMISSION",submission.id,null),actor);
        event(request,actor,"SUBMITTED",null,submission.id,null,null,null,null,now);
        return new Receipt(dto.operationId(),"REPAIR_REQUEST",request.id.toString(),0,null,CurrentUserVo.instant(now),null,null);
    }
    public String reportableLocation(long location) {
        var current=requests.location(location); if(current==null || !current.reportable) throw error(400,"INVALID_LOCATION","请选择范围内可报修位置"); return requests.locationName(location);
    }
    private RepairRequestEntity lockTransition(CurrentUser actor,long id,CurrentUser.Role role) {
        if(actor==null || actor.role()!=role) throw error(403,"FORBIDDEN","没有操作权限");
        var before=requests.selectById(id); if(before==null) throw error(404,"RESOURCE_NOT_FOUND","申请不存在");
        if(role==CurrentUser.Role.REPORTER && before.reporterId!=actor.id()) throw error(403,"RECORD_FORBIDDEN","只能操作本人申请");
        users.lockOrdered(java.util.stream.Stream.of(actor.id(),before.reporterId).distinct().sorted().toList()); access.requireUser(actor,Set.of(role),false);
        var current=requests.lock(id); if(current==null || !current.reporterId.equals(before.reporterId)) throw error(409,"STATE_CONFLICT","申请归属已变化，请重新核对"); return current;
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Receipt returnRequest(CurrentUser actor,long id,ReturnRequestDto dto) {
        var r=lockTransition(actor,id,CurrentUser.Role.ADMIN); uuid(dto.operationId()); String reason=text(dto.reason(),2000,"退回原因");
        byte[] digest=operations.digest(Arrays.asList("RETURN_REQUEST",id,dto.version(),null,reason)); var replay=operations.replay(actor,"RETURN_REQUEST",dto.operationId(),digest); if(replay!=null) return replay;
        version(r.version,dto.version()); if(!r.status.equals("PENDING_REVIEW")) throw error(409,"STATE_CONFLICT","只有待审核申请可退回补充");
        String from=r.status; Instant now=clock.instant().truncatedTo(ChronoUnit.MILLIS); changeState(r,"NEEDS_SUPPLEMENT",now);
        event(r,actor,"RETURNED",from,r.currentSubmissionId,reason,"RETURN_REQUEST",dto.operationId(),digest,now); return receipt(r,dto.operationId(),now);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Receipt withdraw(CurrentUser actor,long id,WithdrawRequestDto dto) {
        var r=lockTransition(actor,id,CurrentUser.Role.REPORTER); uuid(dto.operationId());
        byte[] digest=operations.digest(Arrays.asList("WITHDRAW_REQUEST",id,dto.version(),null)); var replay=operations.replay(actor,"WITHDRAW_REQUEST",dto.operationId(),digest); if(replay!=null) return replay;
        version(r.version,dto.version()); if(!Set.of("PENDING_REVIEW","NEEDS_SUPPLEMENT").contains(r.status) || operations.linkedOrder(r.id)!=null) throw error(409,"STATE_CONFLICT","申请当前不能撤回");
        String from=r.status; Instant now=clock.instant().truncatedTo(ChronoUnit.MILLIS); changeState(r,"WITHDRAWN",now);
        event(r,actor,"WITHDRAWN",from,r.currentSubmissionId,null,"WITHDRAW_REQUEST",dto.operationId(),digest,now); return receipt(r,dto.operationId(),now);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Receipt supplement(CurrentUser actor,long id,SupplementRequestDto dto) {
        var r=lockTransition(actor,id,CurrentUser.Role.REPORTER); uuid(dto.operationId()); var ids=attachmentIds(dto.attachmentIds());
        var values=fields(dto.equipmentName(),dto.equipmentCategory(),dto.addressText(),dto.description(),dto.contactName(),dto.contactPhone());
        Long location=null; if(r.locationMode.equals("STRUCTURED")) location=id(dto.locationId()); else if(dto.locationId()!=null) throw error(400,"INVALID_LOCATION","旧文字申请保留原位置模式，不接受新位置绑定");
        byte[] digest=operations.digest(Arrays.asList("SUPPLEMENT_REQUEST",id,dto.version(),null,location,values,ids)); var replay=operations.replay(actor,"SUPPLEMENT_REQUEST",dto.operationId(),digest); if(replay!=null) return replay;
        version(r.version,dto.version()); if(!r.status.equals("NEEDS_SUPPLEMENT")) throw error(409,"STATE_CONFLICT","只有退回待补充申请可重提");
        var history=requests.submissions(r.id); var submissionIds=history.stream().map(s -> s.id).toList();
        if(ids.isEmpty() && files.selectCount(new QueryWrapper<AttachmentEntity>().in("bound_submission_id",submissionIds).eq("state","BOUND"))==0) throw error(400,"ATTACHMENT_COUNT_INVALID","申请必须有故障图片");
        String snapshot=location==null?null:reportableLocation(location); Instant now=clock.instant().truncatedTo(ChronoUnit.MILLIS); String from=r.status;
        int oldVersion=r.version; r.version++; r.updatedAt=now; r.status="PENDING_REVIEW"; r.locationId=location; r.locationNameSnapshot=snapshot; apply(r,values);
        var submission=saveSubmission(r,actor,history.getFirst().submissionNo+1,"SUPPLEMENT_REQUEST",dto.operationId(),digest,now); r.currentSubmissionId=submission.id;
        var update=new UpdateWrapper<RepairRequestEntity>().eq("id",r.id).eq("version",oldVersion).eq("status","NEEDS_SUPPLEMENT")
            .set("equipment_name",r.equipmentName).set("equipment_category",r.equipmentCategory).set("address_text",r.addressText).set("description",r.description).set("contact_name",r.contactName).set("contact_phone",r.contactPhone)
            .set("location_id",location).set("location_name_snapshot",snapshot).set("current_submission_id",submission.id).set("status",r.status).set("version",r.version).set("updated_at",now);
        if(requests.update(null,update)!=1) throw error(409,"VERSION_CONFLICT","申请已变化，请重新核对");
        attachments.bindTemporary(ids,new AttachmentTarget("REQUEST_SUBMISSION",submission.id,null),actor);
        event(r,actor,"SUPPLEMENTED",from,submission.id,null,null,null,null,now); return receipt(r,dto.operationId(),now);
    }
    private void changeState(RepairRequestEntity r,String to,Instant now) {
        if(requests.update(null,new UpdateWrapper<RepairRequestEntity>().eq("id",r.id).eq("version",r.version).eq("status",r.status).set("status",to).set("version",r.version+1).set("updated_at",now))!=1) throw error(409,"VERSION_CONFLICT","申请已变化，请重新核对");
        r.status=to; r.version++; r.updatedAt=now;
    }
    private static Receipt receipt(RepairRequestEntity r,String operation,Instant now) { return new Receipt(operation,"REPAIR_REQUEST",r.id.toString(),r.version,null,CurrentUserVo.instant(now),null,null); }
    private static List<String> fields(String name,String categoryValue,String address,String description,String contact,String phone) {
        text(name,100,"设备名称"); text(address,500,"详细地址"); text(description,4000,"故障描述"); text(contact,100,"联系人");
        if(phone==null || !phone.matches("^1[3-9][0-9]{9}$")) throw error(400,"VALIDATION_FAILED","联系电话必须为11位大陆手机号");
        return Arrays.asList(name,category(categoryValue),address,description,contact,phone);
    }
    private static void apply(RepairRequestEntity r,List<String> f) { r.equipmentName=f.get(0); r.equipmentCategory=f.get(1); r.addressText=f.get(2); r.description=f.get(3); r.contactName=f.get(4); r.contactPhone=f.get(5); }
    private RequestSubmissionEntity saveSubmission(RepairRequestEntity r,CurrentUser actor,int number,String type,String operation,byte[] digest,Instant now) {
        var s=new RequestSubmissionEntity(); s.id=IdWorker.getId(); s.createdAt=now; s.submittedAt=now; s.requestId=r.id; s.submissionNo=number; s.actorUserId=actor.id();
        s.equipmentName=r.equipmentName; s.equipmentCategory=r.equipmentCategory; s.addressText=r.addressText; s.description=r.description; s.contactName=r.contactName; s.contactPhone=r.contactPhone;
        s.locationMode=r.locationMode; s.locationId=r.locationId; s.locationNameSnapshot=r.locationNameSnapshot; s.operationType=type; s.operationId=operation; s.contentDigest=digest; s.resultVersion=r.version;
        requests.insertSubmission(s); return s;
    }
    public RequestEventEntity event(RepairRequestEntity r,CurrentUser actor,String type,String from,Long submission,String reason,String operationType,String operation,byte[] digest,Instant now) {
        var e=new RequestEventEntity(); e.id=IdWorker.getId(); e.createdAt=now; e.occurredAt=now; e.requestId=r.id; e.actorUserId=actor.id(); e.submissionId=submission; e.eventType=type; e.fromStatus=from; e.toStatus=r.status; e.reason=reason;
        e.operationType=operationType; e.operationId=operation; e.contentDigest=digest; if(operationType!=null) { e.resultResourceType="REPAIR_REQUEST"; e.resultResourceId=r.id; e.resultVersion=r.version; }
        requests.insertEvent(e); return e;
    }
    @Transactional(readOnly=true)
    public PageResult<SummaryVo> list(CurrentUser actor,int page,int size,String keyword,String status,String location) {
        access.requireUser(actor,Set.of(CurrentUser.Role.REPORTER,CurrentUser.Role.ADMIN),false); page(page,size,keyword);
        var query=new QueryWrapper<RepairRequestEntity>(); if(actor.role()==CurrentUser.Role.REPORTER) query.eq("reporter_id",actor.id());
        if(status!=null && !status.isBlank()) { if(!Set.of("PENDING_REVIEW","NEEDS_SUPPLEMENT","APPROVED","WITHDRAWN","DUPLICATE_CLOSED").contains(status)) throw error(400,"INVALID_QUERY","申请状态不合法"); query.eq("status",status); }
        if(location!=null && !location.isBlank()) query.eq("location_id",id(location));
        if(keyword!=null && !keyword.isBlank()) query.and(q -> q.apply("CONVERT(request_no USING utf8mb4) LIKE {0}","%"+keyword+"%").or().like("equipment_name",keyword));
        long count=requests.selectCount(query); query.orderByDesc("created_at","id").last("LIMIT "+size+" OFFSET "+((long)(page-1)*size));
        return new PageResult<>(requests.selectList(query).stream().map(r -> summary(r,actor)).toList(),count,page,size);
    }
    public List<String> actions(RepairRequestEntity r,CurrentUser actor) {
        if(actor.role()==CurrentUser.Role.REPORTER && r.reporterId==actor.id()) {
            if(r.status.equals("NEEDS_SUPPLEMENT")) return List.of("SUPPLEMENT_REQUEST","WITHDRAW_REQUEST");
            if(r.status.equals("PENDING_REVIEW")) return List.of("WITHDRAW_REQUEST");
            if(r.status.equals("APPROVED")) return List.of("VIEW_LINKED_ORDER");
        }
        if(actor.role()==CurrentUser.Role.ADMIN && r.status.equals("PENDING_REVIEW")) return List.of("RETURN_REQUEST","CLOSE_DUPLICATE","APPROVE_AND_ASSIGN");
        return r.status.equals("APPROVED")?List.of("VIEW_LINKED_ORDER"):List.of();
    }
    private SummaryVo summary(RepairRequestEntity r,CurrentUser actor) { return new SummaryVo(r.id.toString(),r.requestNo,r.equipmentName,r.status,r.locationMode,location(r.locationId),r.locationNameSnapshot,CurrentUserVo.instant(r.firstSubmittedAt),CurrentUserVo.instant(r.createdAt),r.version,operations.linkedOrder(r.id),actions(r,actor)); }
    @Transactional(readOnly=true)
    public RepairRequestDetailVo detail(CurrentUser actor,long id) {
        access.requireUser(actor,Set.of(CurrentUser.Role.REPORTER,CurrentUser.Role.ADMIN,CurrentUser.Role.TECHNICIAN),false); var r=requests.selectById(id);
        if(r==null) throw error(404,"RESOURCE_NOT_FOUND","申请不存在");
        var order=orders.forRequest(id); String scope=access.canReadRequest(actor,id)?"FULL":access.historyScope(actor,order);
        if(scope==null) throw error(403,"RECORD_FORBIDDEN","没有查看该申请的权限"); boolean full=scope.equals("FULL");
        var visible=full?null:orders.assignments(order.id).stream().filter(a -> a.technicianId==actor.id()).findFirst().orElseThrow(() -> error(500,"INTERNAL_ERROR","分派材料范围不可用"));
        long snapshot=full?r.currentSubmissionId:visible.visibleSubmissionId; var submission=requests.submission(snapshot);
        if(submission==null || !submission.requestId.equals(r.id)) throw error(500,"INTERNAL_ERROR","提交快照不可用");
        Instant through=full?null:(visible.endedAt==null?visible.assignedAt:visible.endedAt);
        var s=summary(r,actor); var history=requests.submissions(id).stream().filter(h -> full || (h.submissionNo<=submission.submissionNo && !h.submittedAt.isAfter(through))).map(this::submissionVo).toList();
        var current=history.stream().filter(h -> h.id().equals(Long.toString(snapshot))).findFirst().orElseThrow(() -> error(500,"INTERNAL_ERROR","提交快照不可用"));
        var visibleIds=history.stream().map(SubmissionVo::id).collect(java.util.stream.Collectors.toSet());
        // A later opinion can reference an old submission; both the snapshot and relationship end limit it.
        var events=requests.events(id).stream().filter(e -> full || (e.submissionId!=null && visibleIds.contains(e.submissionId.toString()) && !e.occurredAt.isAfter(through)))
            .map(e -> new EventVo(e.id.toString(),e.eventType,e.fromStatus,e.toStatus,account(e.actorUserId),e.reason,CurrentUserVo.instant(e.occurredAt))).toList();
        var matched=r.matchedEquipmentId==null?null:equipment.selectById(r.matchedEquipmentId);
        return new RepairRequestDetailVo(s.id(),s.requestNo(),full?s.equipmentName():current.equipmentName(),s.status(),full?s.locationMode():current.locationMode(),full?s.location():current.location(),full?s.locationNameSnapshot():current.locationNameSnapshot(),s.firstSubmittedAt(),s.createdAt(),full?s.version():submission.resultVersion,s.workOrder(),actor.role()==CurrentUser.Role.TECHNICIAN?List.of():s.allowedActions(),account(r.reporterId),current,history,events,matched==null?null:new EquipmentReferenceVo(matched.id.toString(),matched.equipmentNo,matched.name,matched.locationId==null?null:matched.locationId.toString()),CurrentUserVo.instant(clock.instant()));
    }
    public SubmissionVo submissionVo(RequestSubmissionEntity s) {
        var images=files.selectList(new QueryWrapper<AttachmentEntity>().eq("bound_submission_id",s.id).eq("state","BOUND").orderByAsc("id")).stream().map(AttachmentVo::from).toList();
        return new SubmissionVo(s.id.toString(),s.submissionNo,s.equipmentName,s.equipmentCategory,s.locationMode,location(s.locationId),s.locationNameSnapshot,s.addressText,s.description,s.contactName,s.contactPhone,images,CurrentUserVo.instant(s.submittedAt));
    }
    public LocationReferenceVo location(Long id) { if(id==null) return null; var l=requests.location(id); return l==null?null:new LocationReferenceVo(l.id.toString(),l.code,l.name,l.floorId.toString()); }
    public AccountReferenceVo account(long id) { var u=users.selectById(id); return u==null?null:new AccountReferenceVo(u.id.toString(),u.name); }
}
