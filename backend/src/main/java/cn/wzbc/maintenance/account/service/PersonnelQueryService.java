package cn.wzbc.maintenance.account.service;
import cn.wzbc.maintenance.account.mapper.PersonnelQueryMapper;
import cn.wzbc.maintenance.account.vo.*;
import cn.wzbc.maintenance.common.*;
import cn.wzbc.maintenance.security.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static cn.wzbc.maintenance.common.OperationResultService.*;
@Service
public class PersonnelQueryService {
    private static final Set<String> CATEGORIES=Set.of("ALL","REPORTER_UNFINISHED","REPORTER_COMPLETED","TECHNICIAN_UNFINISHED","TECHNICIAN_COMPLETED");
    private final PersonnelQueryMapper mapper;private final BusinessAccessPolicy access;private final JdbcTemplate db;private final java.time.Clock clock;
    public PersonnelQueryService(PersonnelQueryMapper mapper,BusinessAccessPolicy access,JdbcTemplate db,java.time.Clock clock){this.mapper=mapper;this.access=access;this.db=db;this.clock=clock;}
    public record PersonnelListVo(List<PersonnelVo> records,int page,int pageSize,long listTotal,long allPeopleTotal,Map<String,Long> categoryCounts,long pendingReviewCount,String queriedAt) {}
    private void admin(CurrentUser actor){access.requireUser(actor,Set.of(CurrentUser.Role.ADMIN),false);}
    private String now(){return CurrentUserVo.instant(clock.instant());}
    @Transactional(readOnly=true) public PersonnelListVo list(CurrentUser actor,int page,int size,String keyword,String category,String role,String review,Boolean enabled){
        admin(actor);page(page,size,keyword);if(!CATEGORIES.contains(category)||role!=null&&!Set.of("ADMIN","REPORTER","TECHNICIAN").contains(role)||review!=null&&!Set.of("PENDING","REJECTED","APPROVED").contains(review)||review!=null&&role!=null&&!role.equals("TECHNICIAN"))throw error(400,"INVALID_QUERY","人员查询条件不合法");
        var q=new HashMap<String,Object>();q.put("category",category);q.put("role",role);q.put("reviewStatus",review);q.put("enabled",enabled);q.put("keyword",keyword==null||keyword.isBlank()?null:keyword.trim());q.put("size",size);q.put("offset",(long)(page-1)*size);
        var totals=mapper.totals();var categories=new LinkedHashMap<String,Long>();for(String key:List.of("REPORTER_UNFINISHED","REPORTER_COMPLETED","TECHNICIAN_UNFINISHED","TECHNICIAN_COMPLETED"))categories.put(key,number(totals.get(key)));
        return new PersonnelListVo(mapper.list(q).stream().map(u->vo(actor,u)).toList(),page,size,mapper.count(q),number(totals.get("allPeopleTotal")),categories,number(totals.get("pendingReviewCount")),now());
    }
    private static long number(Object value){return value==null?0:((Number)value).longValue();}
    private PersonnelVo vo(CurrentUser actor,PersonnelQueryMapper.Row u){
        var actions=new ArrayList<String>();if(u.enabled&&u.id!=actor.id())actions.add("DISABLE_ACCOUNT");if(!u.enabled)actions.add("ENABLE_ACCOUNT");if(u.role!=CurrentUser.Role.ADMIN)actions.add("RESET_PASSWORD");if(u.role==CurrentUser.Role.TECHNICIAN&&u.enabled&&u.reviewStatus==CurrentUser.ReviewStatus.PENDING)actions.addAll(List.of("REVIEW_APPROVE","REVIEW_REJECT"));
        Map<String,Object> counts=u.role==CurrentUser.Role.ADMIN?null:u.role==CurrentUser.Role.REPORTER?Map.of("role","REPORTER","pendingRequests",u.pendingRequests,"unfinishedOrders",u.unfinishedOrders,"completedOrders",u.completedOrders):Map.of("role","TECHNICIAN","unfinishedTasks",u.unfinishedTasks,"completedOrders",u.completedOrders);
        return new PersonnelVo(u.id.toString(),u.loginName,u.name,u.phone,u.role,u.enabled,u.reviewStatus,u.version,CurrentUserVo.from(u).capabilities(),actions,u.reviewAttempt,counts);
    }
    @Transactional(readOnly=true) public PersonnelDetailVo history(CurrentUser actor,long target,String category,String status,String keyword,int page,int size){
        admin(actor);page(page,size,keyword);var u=mapper.person(target);if(u==null)throw error(404,"RESOURCE_NOT_FOUND","人员不存在");
        var available=u.role==CurrentUser.Role.REPORTER?List.of("REQUESTS","RELATED_ORDERS"):u.role==CurrentUser.Role.TECHNICIAN?List.of("CURRENT_TASKS","HISTORICAL_ORDERS"):List.<String>of();
        var totals=new LinkedHashMap<String,Long>();for(String c:available){String sql=source(u,c);totals.put(c,db.queryForObject("SELECT COUNT(*) FROM ("+sql+") history",Long.class,target));}
        if(available.isEmpty())return new PersonnelDetailVo(vo(actor,u),available,totals,Map.of(),new PageResult<>(List.of(),0,page,size),now());
        String selected=category==null?available.getFirst():category;if(!available.contains(selected))throw error(400,"INVALID_QUERY","业务分类不适用于该角色");
        if(status!=null&&!Set.of("PENDING_REVIEW","NEEDS_SUPPLEMENT","APPROVED","WITHDRAWN","DUPLICATE_CLOSED","PENDING_ACCEPT","REASSIGN_PENDING","IN_PROGRESS","PENDING_ACCEPTANCE","COMPLETED").contains(status))throw error(400,"INVALID_QUERY","业务状态不合法");
        String source=source(u,selected);var statuses=new LinkedHashMap<String,Long>();db.query("SELECT status,COUNT(*) n FROM ("+source+") history GROUP BY status",r->{statuses.put(r.getString("status"),r.getLong("n"));},target);
        var args=new ArrayList<Object>();args.add(target);String where=" WHERE 1=1";if(status!=null){where+=" AND status=?";args.add(status);}if(keyword!=null&&!keyword.isBlank()){where+=" AND (LOCATE(?,CONVERT(business_no USING utf8mb4))>0 OR LOCATE(?,equipment_name)>0)";args.add(keyword.trim());args.add(keyword.trim());}
        long total=db.queryForObject("SELECT COUNT(*) FROM ("+source+") history"+where,Long.class,args.toArray());args.add(size);args.add((long)(page-1)*size);
        var records=db.query("SELECT * FROM ("+source+") history"+where+" ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?",(r,n)->{
            var relations=new ArrayList<String>();if(u.role==CurrentUser.Role.REPORTER)relations.add("SUBMITTER");else{
                long order=r.getLong("id");if(r.getLong("assigned_technician_id")==target||r.getLong("accepted_technician_id")==target)if(!r.getString("status").equals("COMPLETED"))relations.add("CURRENT_ASSIGNEE");
                if(r.getLong("accepted_technician_id")==target){relations.add("ACTUAL_REPAIRER");if(r.getString("status").equals("COMPLETED"))relations.add("COMPLETED_BY_ME");}
                for(String outcome:db.queryForList("SELECT DISTINCT outcome FROM work_order_assignment WHERE work_order_id=? AND technician_id=? AND outcome IN ('REFUSED','REPLACED')",String.class,order,target))relations.add(outcome);
            }
            return new PersonnelDetailVo.BusinessHistoryItemVo(selected.equals("REQUESTS")?"REPAIR_REQUEST":"WORK_ORDER",r.getString("id"),r.getString("business_no"),r.getString("equipment_name"),r.getString("status"),CurrentUserVo.instant(r.getTimestamp("created_at").toInstant()),relations,true);
        },args.toArray());
        return new PersonnelDetailVo(vo(actor,u),available,totals,statuses,new PageResult<>(records,total,page,size),now());
    }
    private String source(PersonnelQueryMapper.Row u,String category){
        if(category.equals("REQUESTS"))return "SELECT id,request_no business_no,equipment_name,status,created_at FROM repair_request WHERE reporter_id=?";
        String condition=category.equals("RELATED_ORDERS")?"w.reporter_id=p.id":category.equals("CURRENT_TASKS")?"w.status IN ('PENDING_ACCEPT','IN_PROGRESS','PENDING_ACCEPTANCE') AND (w.assigned_technician_id=p.id OR w.accepted_technician_id=p.id)":"w.assigned_technician_id=p.id OR w.accepted_technician_id=p.id OR EXISTS(SELECT 1 FROM work_order_assignment a WHERE a.work_order_id=w.id AND a.technician_id=p.id)";
        return "SELECT w.id,w.work_order_no business_no,r.equipment_name,w.status,w.created_at,w.assigned_technician_id,w.accepted_technician_id FROM work_order w JOIN repair_request r ON r.id=w.request_id JOIN user_account p ON p.id=? WHERE ("+condition+")";
    }
}
