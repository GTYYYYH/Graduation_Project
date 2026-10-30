package cn.wzbc.maintenance.security;
import cn.wzbc.maintenance.attachment.AttachmentTarget;
import cn.wzbc.maintenance.attachment.entity.AttachmentEntity;
import cn.wzbc.maintenance.account.entity.UserEntity;
import cn.wzbc.maintenance.account.mapper.UserMapper;
import cn.wzbc.maintenance.repairrequest.mapper.RepairRequestMapper;
import cn.wzbc.maintenance.workorder.mapper.WorkOrderMapper;
import cn.wzbc.maintenance.workorder.entity.WorkOrderEntity;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.util.Set;
import static cn.wzbc.maintenance.common.OperationResultService.error;
import org.springframework.stereotype.Component;
@Component
public class BusinessAccessPolicy {
    private final UserMapper users; private final RepairRequestMapper requests; private final WorkOrderMapper orders;
    public BusinessAccessPolicy(UserMapper users,RepairRequestMapper requests,WorkOrderMapper orders) { this.users=users; this.requests=requests; this.orders=orders; }
    public UserEntity requireUser(CurrentUser user,Set<CurrentUser.Role> roles,boolean lock) {
        if(user==null || !roles.contains(user.role())) throw error(403,"FORBIDDEN","没有操作权限");
        var actual=lock?users.lock(user.id()):users.selectById(user.id());
        if(actual==null || !actual.enabled) throw error(401,"ACCOUNT_DISABLED","登录身份已失效");
        if(!roles.contains(actual.role)) throw error(403,"FORBIDDEN","没有操作权限"); return actual;
    }
    public boolean canReadRequest(CurrentUser user,long id) {
        if(user==null || !user.enabled()) return false; var request=requests.selectById(id); if(request==null) return false;
        return user.role()==CurrentUser.Role.ADMIN || (user.role()==CurrentUser.Role.REPORTER && request.reporterId==user.id());
    }
    public boolean canReadAttachment(CurrentUser user,AttachmentEntity file) {
        if(user==null || !user.enabled()) return false;
        if(file.state.equals("TEMPORARY")) return file.uploaderId==user.id();
        if(!file.state.equals("BOUND")) return false;
        return canReadAttachment(user,new AttachmentTarget(file.boundSubmissionId!=null?"REQUEST_SUBMISSION":"REPAIR_ROUND",file.boundSubmissionId!=null?file.boundSubmissionId:file.boundRoundId,null));
    }
    public boolean canReadAttachment(CurrentUser user,AttachmentTarget target) {
        if(target.kind().equals("REQUEST_SUBMISSION")) {
            var submission=requests.submission(target.id()); if(submission==null) return false;
            if(canReadRequest(user,submission.requestId)) return true;
            var order=orders.forRequest(submission.requestId); String scope=historyScope(user,order); if(scope==null) return false;
            if(scope.equals("FULL")) return true;
            return orders.assignments(order.id).stream().filter(a -> a.technicianId==user.id()).anyMatch(a -> { var visible=requests.submission(a.visibleSubmissionId); return visible!=null && visible.requestId.equals(submission.requestId) && submission.submissionNo<=visible.submissionNo; });
        }
        if(target.kind().equals("REPAIR_ROUND")) { var round=orders.round(target.id()); return round!=null && "FULL".equals(historyScope(user,orders.selectById(round.workOrderId))); }
        return false;
    }
    public String historyScope(CurrentUser user,WorkOrderEntity order) {
        if(user==null || !user.enabled() || order==null) return null;
        if(user.role()==CurrentUser.Role.ADMIN || (user.role()==CurrentUser.Role.REPORTER && order.reporterId==user.id())) return "FULL";
        if(user.role()!=CurrentUser.Role.TECHNICIAN || user.reviewStatus()!=CurrentUser.ReviewStatus.APPROVED) return null;
        if((order.acceptedTechnicianId!=null && order.acceptedTechnicianId==user.id()) || (order.currentRound==0 && order.assignedTechnicianId!=null && order.assignedTechnicianId==user.id())) return "FULL";
        return orders.assignments(order.id).stream().anyMatch(a -> a.technicianId==user.id())?"ASSIGNMENT_ONLY":null;
    }
    public QueryWrapper<WorkOrderEntity> orderQuery(CurrentUser user,String scope) {
        requireUser(user,Set.of(CurrentUser.Role.ADMIN,CurrentUser.Role.REPORTER,CurrentUser.Role.TECHNICIAN),false);
        var q=new QueryWrapper<WorkOrderEntity>();
        if(user.role()==CurrentUser.Role.REPORTER) q.eq("reporter_id",user.id());
        if(user.role()==CurrentUser.Role.TECHNICIAN) {
            if(user.reviewStatus()!=CurrentUser.ReviewStatus.APPROVED) throw error(403,"REVIEW_REQUIRED","通过审核后才可查看本人任务");
            q.and(p -> p.eq("assigned_technician_id",user.id()).or().eq("accepted_technician_id",user.id()).or().apply("EXISTS(SELECT 1 FROM work_order_assignment a WHERE a.work_order_id=work_order.id AND a.technician_id={0})",user.id()));
            String current="status<>'COMPLETED' AND (accepted_technician_id={0} OR (current_round=0 AND assigned_technician_id={0}))";
            if(scope.equals("CURRENT")) q.apply("("+current+")",user.id());
            if(scope.equals("HISTORY")) q.apply("(status='COMPLETED' OR NOT (COALESCE(accepted_technician_id,0)={0} OR (current_round=0 AND COALESCE(assigned_technician_id,0)={0})))",user.id());
        } else if(scope.equals("HISTORY")) q.eq("status","COMPLETED");
        else if(scope.equals("CURRENT")) q.ne("status","COMPLETED");
        return q;
    }
}
