package cn.wzbc.maintenance.common;

import cn.wzbc.maintenance.security.*;
import cn.wzbc.maintenance.account.vo.CurrentUserVo;
import cn.wzbc.maintenance.repairrequest.vo.RepairRequestDetailVo.WorkOrderLinkVo;
import cn.wzbc.maintenance.workorder.mapper.WorkOrderMapper;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import tools.jackson.databind.ObjectMapper;

/** Receipts live with the business records; no second operation-log table. */
@Service
public class OperationResultService {
    private final JdbcTemplate db; private final ObjectMapper json; private final BusinessAccessPolicy access; private final WorkOrderMapper orders;
    public OperationResultService(JdbcTemplate db,ObjectMapper json,BusinessAccessPolicy access,WorkOrderMapper orders) { this.db=db; this.json=json; this.access=access; this.orders=orders; }
    public record Receipt(String operationId,String resourceType,String resourceId,Integer resultVersion,Integer resultRound,String committedAt,String relatedResourceType,String relatedResourceId) {}
    public record OperationResultVo(String kind,Receipt receipt) {}
    public byte[] digest(Object fields) { try { return MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(fields)); } catch(Exception failure) { throw new IllegalStateException("提交摘要生成失败"); } }
    public Receipt replay(CurrentUser actor,String type,String operation,byte[] digest) {
        var row=find(actor,type,operation); if(row==null) return null;
        if(!MessageDigest.isEqual(digest,(byte[])row.get("content_digest"))) throw error(409,"OPERATION_CONTENT_MISMATCH","同一操作标识的内容不一致");
        return receipt(actor,type,operation,row);
    }
    @Transactional(readOnly=true)
    public OperationResultVo query(CurrentUser actor,String type,String operation) {
        access.requireUser(actor,Set.of(CurrentUser.Role.REPORTER,CurrentUser.Role.TECHNICIAN,CurrentUser.Role.ADMIN),false); uuid(operation);
        var row=find(actor,type,operation); return row==null?new OperationResultVo("NOT_CONFIRMED",null):new OperationResultVo("SUCCEEDED",receipt(actor,type,operation,row));
    }
    private Map<String,Object> find(CurrentUser actor,String type,String operation) {
        if(actor==null) throw error(403,"FORBIDDEN","没有操作权限"); uuid(operation);
        String sql=switch(type) {
            case "CREATE_REQUEST","SUPPLEMENT_REQUEST" -> "SELECT content_digest,request_id resource_id,'REPAIR_REQUEST' resource_type,result_version,NULL result_round,submitted_at committed_at FROM repair_request_submission WHERE actor_user_id=? AND operation_type=? AND operation_id=?";
            case "RETURN_REQUEST","WITHDRAW_REQUEST","DUPLICATE_CLOSE","APPROVE_ASSIGN" -> "SELECT content_digest,result_resource_id resource_id,result_resource_type resource_type,result_version,result_round,occurred_at committed_at FROM repair_request_event WHERE actor_user_id=? AND operation_type=? AND operation_id=?";
            case "EQUIPMENT_CREATE","EQUIPMENT_UPDATE","EQUIPMENT_ENABLE","EQUIPMENT_DISABLE" -> "SELECT content_digest,equipment_id resource_id,'EQUIPMENT' resource_type,result_version,NULL result_round,occurred_at committed_at FROM equipment_event WHERE actor_user_id=? AND operation_type=? AND operation_id=?";
            case "REVIEW_RESUBMIT","REVIEW_DECIDE","PROFILE_UPDATE","PASSWORD_CHANGE","PASSWORD_RESET","ACCOUNT_ENABLE","ACCOUNT_DISABLE" -> "SELECT content_digest,result_resource_id resource_id,'ACCOUNT' resource_type,result_version,NULL result_round,occurred_at committed_at FROM account_event WHERE actor_user_id=? AND operation_type=? AND operation_id=?";
            case "ACCEPTED","REFUSED","ASSIGNED","PROGRESS_ADDED","RESULT_SUBMITTED","ACCEPTANCE" -> "SELECT content_digest,result_resource_id resource_id,'WORK_ORDER' resource_type,result_version,result_round,occurred_at committed_at FROM work_order_event WHERE actor_user_id=? AND operation_type=? AND operation_id=?";
            default -> throw error(400,"INVALID_QUERY","操作类型不合法或尚未开放");
        };
        var rows=db.queryForList(sql,actor.id(),type,operation); return rows.isEmpty()?null:rows.getFirst();
    }
    private Receipt receipt(CurrentUser actor,String type,String operation,Map<String,Object> row) {
        long resource=((Number)row.get("resource_id")).longValue(); String resourceType=row.get("resource_type").toString();
        if(resourceType.equals("REPAIR_REQUEST") && !access.canReadRequest(actor,resource)) throw error(403,"RECORD_FORBIDDEN","原操作记录已不在可查看范围");
        if(resourceType.equals("WORK_ORDER") && access.historyScope(actor,orders.selectById(resource))==null) throw error(403,"RECORD_FORBIDDEN","原操作记录已不在可查看范围");
        if(resourceType.equals("EQUIPMENT")) access.requireUser(actor,Set.of(CurrentUser.Role.ADMIN),false);
        if(resourceType.equals("ACCOUNT") && actor.role()!=CurrentUser.Role.ADMIN && actor.id()!=resource) throw error(403,"RECORD_FORBIDDEN","没有查看原操作的权限");
        var related=type.equals("APPROVE_ASSIGN")?linkedOrder(resource):null;
        Object at=row.get("committed_at"); Instant time=at instanceof Timestamp t?t.toInstant():((java.time.LocalDateTime)at).toInstant(java.time.ZoneOffset.UTC);
        return new Receipt(operation,resourceType,Long.toString(resource),number(row.get("result_version")),number(row.get("result_round")),CurrentUserVo.instant(time),related==null?null:"WORK_ORDER",related==null?null:related.id());
    }
    public WorkOrderLinkVo linkedOrder(long request) {
        if(db.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='work_order'",Integer.class)==0) return null;
        var rows=db.query("SELECT id,work_order_no,status FROM work_order WHERE request_id=?",(r,n) -> new WorkOrderLinkVo(r.getString(1),r.getString(2),r.getString(3)),request);
        return rows.isEmpty()?null:rows.getFirst();
    }
    private static Integer number(Object value) { return value==null?null:((Number)value).intValue(); }
    public static long id(String value) { try { if(value==null || !value.matches("[1-9][0-9]{0,18}")) throw new NumberFormatException(); return Long.parseLong(value); } catch(NumberFormatException failure) { throw error(400,"VALIDATION_FAILED","ID不合法"); } }
    public static void uuid(String value) { if(value==null || !value.matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")) throw error(400,"VALIDATION_FAILED","操作标识不合法"); }
    public static String text(String value,int max,String label) { if(value==null || value.isBlank() || value.codePointCount(0,value.length())>max) throw error(400,"VALIDATION_FAILED",label+"必填，且不超过"+max+"字"); return value; }
    public static String category(String value) { return value==null || value.isBlank()?null:text(value,100,"设备类别"); }
    public static List<Long> attachmentIds(List<String> values) {
        if(values==null || values.size()>5) throw error(400,"ATTACHMENT_COUNT_INVALID","每次新增图片最多5张");
        var ids=values.stream().map(OperationResultService::id).sorted().toList(); if(ids.stream().distinct().count()!=ids.size()) throw error(400,"ATTACHMENT_COUNT_INVALID","同一图片不能重复提交"); return ids;
    }
    public static void version(int actual,Integer supplied) { if(supplied==null || supplied<0 || actual!=supplied || actual==Integer.MAX_VALUE) throw error(409,"VERSION_CONFLICT","记录已变化，请重新核对"); }
    public static void page(int page,int size,String keyword) { if(page<1 || size<1 || size>100 || (keyword!=null && keyword.codePointCount(0,keyword.length())>100)) throw error(400,"INVALID_QUERY","分页或搜索条件不合法"); }
    public static BusinessException error(int status,String code,String message) { return new BusinessException(HttpStatus.valueOf(status),code,message); }
    @RestController
    public static class OperationsController {
        private final OperationResultService service;
        public OperationsController(OperationResultService service) { this.service=service; }
        @GetMapping("/api/operations/{operationId}") public ApiResponse<OperationResultVo> query(@AuthenticationPrincipal CurrentUser actor,@PathVariable String operationId,@RequestParam String operation,HttpServletRequest request) { return ApiResponse.success(service.query(actor,operation,operationId),request); }
    }
}


