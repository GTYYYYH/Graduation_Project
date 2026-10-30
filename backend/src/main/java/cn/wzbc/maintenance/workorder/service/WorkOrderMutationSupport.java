package cn.wzbc.maintenance.workorder.service;

import cn.wzbc.maintenance.account.mapper.UserMapper;
import cn.wzbc.maintenance.common.OperationResultService;
import cn.wzbc.maintenance.common.OperationResultService.Receipt;
import cn.wzbc.maintenance.account.vo.CurrentUserVo;
import cn.wzbc.maintenance.security.*;
import cn.wzbc.maintenance.workorder.entity.*;
import cn.wzbc.maintenance.workorder.mapper.WorkOrderMapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;
import static cn.wzbc.maintenance.common.OperationResultService.*;

/** The B05 lock and persisted-receipt boundary shared by all order writes. */
@Component
public class WorkOrderMutationSupport {
    final UserMapper users; final WorkOrderMapper orders; final BusinessAccessPolicy access; final OperationResultService operations; final Clock clock;
    public WorkOrderMutationSupport(UserMapper users,WorkOrderMapper orders,BusinessAccessPolicy access,OperationResultService operations,Clock clock) {
        this.users=users; this.orders=orders; this.access=access; this.operations=operations; this.clock=clock;
    }
    public record LockedOrder(WorkOrderEntity order,Set<Long> lockedUsers) {
        public void requireCurrentParticipants() {
            if(Stream.of(order.reporterId,order.assignedTechnicianId,order.acceptedTechnicianId).filter(Objects::nonNull).anyMatch(id -> !lockedUsers.contains(id)))
                throw error(409,"STATE_CONFLICT","工单参与人员已变化，请重新核对");
        }
    }
    public LockedOrder lock(CurrentUser actor,long id,Set<CurrentUser.Role> roles,Long candidate) {
        access.requireUser(actor,roles,false);
        var seen=orders.selectById(id); if(seen==null) throw error(404,"RESOURCE_NOT_FOUND","工单不存在");
        var ids=Stream.of(actor.id(),seen.reporterId,seen.assignedTechnicianId,seen.acceptedTechnicianId,candidate).filter(Objects::nonNull).distinct().sorted().toList();
        users.lockOrdered(ids); access.requireUser(actor,roles,false);
        var order=orders.lock(id);
        if(order==null || !Objects.equals(seen.reporterId,order.reporterId)) throw error(409,"STATE_CONFLICT","工单归属已变化");
        return new LockedOrder(order,Set.copyOf(ids));
    }
    public Instant now() { return clock.instant().truncatedTo(ChronoUnit.MILLIS); }
    public void change(WorkOrderEntity order,String expected,String next,Long assignee,Long assignment,Long accepted,Instant acceptedAt,Instant completedAt,int round,Instant now) {
        var update=new UpdateWrapper<WorkOrderEntity>().eq("id",order.id).eq("version",order.version).eq("status",expected)
            .set("status",next).set("version",order.version+1).set("updated_at",now).set("assigned_technician_id",assignee)
            .set("current_assignment_id",assignment).set("accepted_technician_id",accepted).set("accepted_at",acceptedAt).set("completed_at",completedAt).set("current_round",round);
        if(orders.update(null,update)!=1) throw error(409,"VERSION_CONFLICT","工单已变化，请重新核对");
        order.version++; order.status=next; order.currentRound=round;
    }
    public Receipt event(WorkOrderEntity order,CurrentUser actor,String type,String from,String to,String reason,Long assignment,Long round,Long progress,String operation,byte[] digest,long resource,Instant now) {
        var e=new WorkOrderEventEntity(); e.id=IdWorker.getId(); e.createdAt=now; e.workOrderId=order.id; e.actorUserId=actor.id(); e.assignmentId=assignment; e.roundId=round; e.progressId=progress;
        e.eventType=type; e.fromStatus=from; e.toStatus=to; e.reason=reason; e.occurredAt=now; e.operationType=Set.of("COMPLETED","ACCEPTANCE_FAILED").contains(type)?"ACCEPTANCE":type; e.operationId=operation; e.contentDigest=digest; e.resultResourceId=resource; e.resultVersion=order.version; e.resultRound=order.currentRound;
        orders.insertEvent(e); return new Receipt(operation,"WORK_ORDER",Long.toString(order.id),order.version,order.currentRound,CurrentUserVo.instant(now),null,null);
    }
    public Receipt replay(CurrentUser actor,String type,String operation,byte[] digest) { return operations.replay(actor,type,operation,digest); }
}
