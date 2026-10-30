package cn.wzbc.maintenance.workorder.service;

import cn.wzbc.maintenance.account.mapper.UserMapper;
import cn.wzbc.maintenance.account.vo.CurrentUserVo;
import cn.wzbc.maintenance.common.PageResult;
import cn.wzbc.maintenance.security.CurrentUser;
import cn.wzbc.maintenance.workorder.mapper.AssignmentCandidateMapper;
import cn.wzbc.maintenance.workorder.vo.AssignmentCandidateVo;
import cn.wzbc.maintenance.workorder.vo.AssignmentCandidateVo.AssignmentCandidatesVo;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static cn.wzbc.maintenance.common.OperationResultService.*;

@Service
public class AssignmentRecommendationService {
    private final AssignmentService assignments;private final UserMapper users;private final AssignmentCandidateMapper mapper;private final Clock clock;
    public AssignmentRecommendationService(AssignmentService assignments,UserMapper users,AssignmentCandidateMapper mapper,Clock clock) { this.assignments=assignments;this.users=users;this.mapper=mapper;this.clock=clock; }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public AssignmentCandidatesVo candidates(CurrentUser actor,String context,String requestId,String workOrderId,int page,int size,String keyword) {
        page(page,size,keyword);
        Instant now=clock.instant();var zone=ZoneId.of("Asia/Shanghai");var date=now.atZone(zone).toLocalDate();
        var target=assignments.candidateContext(actor,context,requestId,workOrderId);
        var qualified=users.selectList(assignments.qualifiedQuery(target));
        var metrics=mapper.metrics(date.atStartOfDay(zone).toInstant(),date.plusDays(1).atStartOfDay(zone).toInstant()).stream().collect(Collectors.toMap(m->m.id,Function.identity()));
        var refused=new HashMap<Long,List<AssignmentService.RefusalReferenceVo>>();
        if(target.orderId()!=null) for(var a:mapper.refusals(target.orderId())) refused.computeIfAbsent(a.technicianId,k->new ArrayList<>()).add(new AssignmentService.RefusalReferenceVo(a.id.toString(),a.reason,CurrentUserVo.instant(a.decisionAt)));
        var sorted=qualified.stream().sorted(Comparator
            .comparingLong((cn.wzbc.maintenance.account.entity.UserEntity u)->metrics.get(u.id).doubledLoad())
            .thenComparingLong(u->metrics.get(u.id).todayCompleted)
            .thenComparing(u->metrics.get(u.id).lastAssignedAt,Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparingLong(u->u.id)).toList();
        var rows=sorted.stream().map(u->{var m=metrics.get(u.id);var history=refused.getOrDefault(u.id,List.of());
            return new AssignmentCandidateVo(u.id.toString(),u.name,u.enabled,"APPROVED",history,AssignmentService.eligible(u),m.W,m.I,m.R,m.A,m.doubledLoad()/2.0,m.todayCompleted,CurrentUserVo.instant(m.lastAssignedAt),
                history.isEmpty()?"按负荷→今日完成量→最近成功分派→人员ID排序；负荷高仍可人工选择":"本工单曾拒单，不列入优先推荐；确认后仍可人工选择");}).toList();
        var preferred=rows.stream().filter(r->r.refusal().isEmpty()).toList();
        // Keyword only filters the complete list, after global ranking; never changes Top5.
        String search=keyword==null?"":keyword.trim().toLowerCase(Locale.ROOT);
        var matched=new HashSet<String>();for(var u:sorted)if(search.isEmpty() || u.name.toLowerCase(Locale.ROOT).contains(search) || u.loginName.toLowerCase(Locale.ROOT).contains(search))matched.add(u.id.toString());
        var filtered=rows.stream().filter(r->matched.contains(r.id())).toList();long offset=(long)(page-1)*size;
        var records=offset>=filtered.size()?List.<AssignmentCandidateVo>of():filtered.subList((int)offset,(int)Math.min(offset+size,filtered.size()));
        return new AssignmentCandidatesVo(preferred.stream().limit(5).toList(),new PageResult<>(records,filtered.size(),page,size),rows.size(),preferred.size(),CurrentUserVo.instant(now),date.toString());
    }
}
