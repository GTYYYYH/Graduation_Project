package cn.wzbc.maintenance.account.service;
import cn.wzbc.maintenance.account.vo.*;
import cn.wzbc.maintenance.security.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static cn.wzbc.maintenance.common.OperationResultService.*;
@Service
public class WorkbenchQueryService {
    private final BusinessAccessPolicy access;private final JdbcTemplate db;private final java.time.Clock clock;
    public WorkbenchQueryService(BusinessAccessPolicy access,JdbcTemplate db,java.time.Clock clock){this.access=access;this.db=db;this.clock=clock;}
    public record WorkbenchVo(Map<String,Long> counts,List<PersonnelDetailVo.BusinessHistoryItemVo> items,String queriedAt) {}
    @Transactional(readOnly=true) public WorkbenchVo query(CurrentUser actor){
        var u=access.requireUser(actor,Set.of(CurrentUser.Role.values()),false);if(u.role==CurrentUser.Role.TECHNICIAN&&u.reviewStatus!=CurrentUser.ReviewStatus.APPROVED)throw error(403,"REVIEW_REQUIRED","资格通过后才开放维修工作台");
        var counts=new LinkedHashMap<String,Long>();String requests="SELECT 'REPAIR_REQUEST' resource_type,r.id,r.request_no business_no,r.equipment_name,r.status,r.created_at FROM repair_request r WHERE ",orders="SELECT 'WORK_ORDER' resource_type,w.id,w.work_order_no business_no,r.equipment_name,w.status,w.created_at FROM work_order w JOIN repair_request r ON r.id=w.request_id WHERE ";String source;Object[] args;
        if(u.role==CurrentUser.Role.REPORTER){
            counts.put("pendingReview",count("SELECT COUNT(*) FROM repair_request WHERE reporter_id=? AND status='PENDING_REVIEW'",u.id));counts.put("needsSupplement",count("SELECT COUNT(*) FROM repair_request WHERE reporter_id=? AND status='NEEDS_SUPPLEMENT'",u.id));counts.put("pendingAcceptance",count("SELECT COUNT(*) FROM work_order WHERE reporter_id=? AND status='PENDING_ACCEPTANCE'",u.id));
            source=requests+"r.reporter_id=? AND r.status IN ('PENDING_REVIEW','NEEDS_SUPPLEMENT') UNION ALL "+orders+"w.reporter_id=? AND w.status='PENDING_ACCEPTANCE'";args=new Object[]{u.id,u.id};
        }else if(u.role==CurrentUser.Role.TECHNICIAN){
            counts.put("pendingAccept",count("SELECT COUNT(*) FROM work_order WHERE assigned_technician_id=? AND status='PENDING_ACCEPT'",u.id));counts.put("inProgress",count("SELECT COUNT(*) FROM work_order WHERE accepted_technician_id=? AND status='IN_PROGRESS' AND current_round=1",u.id));counts.put("rework",count("SELECT COUNT(*) FROM work_order WHERE accepted_technician_id=? AND status='IN_PROGRESS' AND current_round>1",u.id));counts.put("pendingAcceptance",count("SELECT COUNT(*) FROM work_order WHERE accepted_technician_id=? AND status='PENDING_ACCEPTANCE'",u.id));
            source=orders+"(w.assigned_technician_id=? AND w.status='PENDING_ACCEPT') OR (w.accepted_technician_id=? AND w.status IN ('IN_PROGRESS','PENDING_ACCEPTANCE'))";args=new Object[]{u.id,u.id};
        }else{
            counts.put("pendingReview",count("SELECT COUNT(*) FROM repair_request WHERE status='PENDING_REVIEW'"));counts.put("reassignPending",count("SELECT COUNT(*) FROM work_order WHERE status='REASSIGN_PENDING'"));
            source=requests+"r.status='PENDING_REVIEW' UNION ALL "+orders+"w.status='REASSIGN_PENDING'";args=new Object[]{};
        }
        var items=db.query("SELECT * FROM ("+source+") todo ORDER BY created_at,id LIMIT 20",(r,n)->new PersonnelDetailVo.BusinessHistoryItemVo(r.getString("resource_type"),r.getString("id"),r.getString("business_no"),r.getString("equipment_name"),r.getString("status"),CurrentUserVo.instant(r.getTimestamp("created_at").toInstant()),u.role==CurrentUser.Role.ADMIN?List.of():u.role==CurrentUser.Role.REPORTER?List.of("SUBMITTER"):List.of("CURRENT_ASSIGNEE"),true),args);
        return new WorkbenchVo(counts,items,CurrentUserVo.instant(clock.instant()));
    }
    private long count(String sql,Object...args){return db.queryForObject(sql,Long.class,args);}
}
