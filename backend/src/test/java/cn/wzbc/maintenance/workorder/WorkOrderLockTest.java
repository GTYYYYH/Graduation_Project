package cn.wzbc.maintenance.workorder;

import cn.wzbc.maintenance.account.mapper.UserMapper;
import cn.wzbc.maintenance.common.*;
import cn.wzbc.maintenance.security.*;
import cn.wzbc.maintenance.workorder.dto.RefuseWorkOrderDto;
import cn.wzbc.maintenance.workorder.entity.WorkOrderEntity;
import cn.wzbc.maintenance.workorder.mapper.WorkOrderMapper;
import cn.wzbc.maintenance.workorder.service.*;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Deterministic mapper boundary interleaving; real HTTP races remain in B05BoundaryIT. */
class WorkOrderLockTest {
    private final UserMapper users=mock(UserMapper.class);
    private final WorkOrderMapper orders=mock(WorkOrderMapper.class);
    private final OperationResultService operations=mock(OperationResultService.class);
    private final CurrentUser actor=new CurrentUser(3,CurrentUser.Role.TECHNICIAN,true,CurrentUser.ReviewStatus.APPROVED,0);
    private final String operation="a133f8fa-165c-42a2-92e8-915d9755ddbd";
    private final WorkOrderTransitionService transitions=new WorkOrderTransitionService(new WorkOrderMutationSupport(users,orders,mock(BusinessAccessPolicy.class),operations,Clock.systemUTC()));
    private WorkOrderEntity order(long assigned) {
        var order=new WorkOrderEntity(); order.id=10L; order.reporterId=2L; order.assignedTechnicianId=assigned; order.version=0; order.currentRound=0; order.status="PENDING_ACCEPT"; return order;
    }
    private void changedWhileWaiting() {
        when(orders.selectById(10L)).thenReturn(order(3));
        // A concurrent reassignment wins after the nonlocking read, before the order lock.
        when(orders.lock(10L)).thenReturn(order(4));
    }
    @Test void new_unlocked_participant_aborts_before_old_actor_ownership_check() {
        changedWhileWaiting();
        var failure=assertThrows(BusinessException.class,() -> transitions.refuse(actor,10,new RefuseWorkOrderDto(0,"无法到场",operation)));
        assertEquals(409,failure.status().value()); assertEquals("STATE_CONFLICT",failure.code());
        verify(users).lockOrdered(List.of(2L,3L)); verifyNoMoreInteractions(users);
    }
    @Test void persisted_refusal_replay_precedes_mapping_guard_after_a_later_reassignment() {
        changedWhileWaiting();
        var receipt=new OperationResultService.Receipt(operation,"WORK_ORDER","10",1,0,"2026-10-18T00:00:00.000Z",null,null);
        when(operations.replay(eq(actor),eq("REFUSED"),eq(operation),any())).thenReturn(receipt);
        assertSame(receipt,transitions.refuse(actor,10,new RefuseWorkOrderDto(0,"无法到场",operation)));
        verify(users).lockOrdered(List.of(2L,3L)); verifyNoMoreInteractions(users);
    }
    @Test void changed_mapping_is_safe_when_the_new_participant_was_already_locked_as_candidate() {
        changedWhileWaiting();
        var support=new WorkOrderMutationSupport(users,orders,mock(BusinessAccessPolicy.class),operations,Clock.systemUTC());
        var locked=support.lock(actor,10,java.util.Set.of(CurrentUser.Role.TECHNICIAN),4L);
        assertDoesNotThrow(locked::requireCurrentParticipants);
        verify(users).lockOrdered(List.of(2L,3L,4L)); verifyNoMoreInteractions(users);
    }
}
