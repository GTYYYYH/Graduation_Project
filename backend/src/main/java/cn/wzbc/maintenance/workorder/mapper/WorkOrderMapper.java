package cn.wzbc.maintenance.workorder.mapper;
import cn.wzbc.maintenance.workorder.entity.*;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.*;
@Mapper
public interface WorkOrderMapper extends BaseMapper<WorkOrderEntity> {
    @Select("SELECT * FROM work_order WHERE id=#{id} FOR UPDATE") WorkOrderEntity lock(long id);
    @Select("SELECT * FROM work_order_assignment WHERE id=#{id} FOR UPDATE") AssignmentEntity lockAssignment(long id);
    @Select("SELECT * FROM repair_round WHERE work_order_id=#{order} AND round_no=#{round} FOR UPDATE") RepairRoundEntity lockRound(@Param("order") long order,@Param("round") int round);
    @Select("SELECT * FROM work_order_assignment WHERE work_order_id=#{id} AND technician_id=#{technician} AND outcome='REFUSED' ORDER BY assignment_no DESC LIMIT 1") AssignmentEntity refusedBy(@Param("id") long id,@Param("technician") long technician);
    @Select("SELECT * FROM work_order WHERE request_id=#{id}") WorkOrderEntity forRequest(long id);
    @Select("SELECT * FROM work_order_assignment WHERE work_order_id=#{id} ORDER BY assignment_no DESC,id DESC") List<AssignmentEntity> assignments(long id);
    @Select("SELECT * FROM repair_round WHERE work_order_id=#{id} ORDER BY round_no,id") List<RepairRoundEntity> rounds(long id);
    @Select("SELECT * FROM repair_round WHERE id=#{id}") RepairRoundEntity round(long id);
    @Select("SELECT * FROM repair_progress WHERE round_id=#{id} ORDER BY recorded_at,id") List<ProgressEntity> progress(long id);
    @Select("SELECT * FROM work_order_event WHERE work_order_id=#{id} ORDER BY occurred_at,id") List<WorkOrderEventEntity> events(long id);
    @Insert("INSERT INTO work_order_assignment(id,created_at,work_order_id,assignment_no,technician_id,assigned_by,visible_submission_id,assigned_at,outcome) VALUES(#{id},#{createdAt},#{workOrderId},#{assignmentNo},#{technicianId},#{assignedBy},#{visibleSubmissionId},#{assignedAt},#{outcome})") int insertAssignment(AssignmentEntity a);
    @Update("UPDATE work_order_assignment SET ended_at=#{endedAt},outcome=#{outcome},reason=#{reason},decision_at=#{decisionAt} WHERE id=#{id} AND outcome='PENDING' AND ended_at IS NULL") int endAssignment(AssignmentEntity a);
    @Update("UPDATE work_order_assignment SET outcome='ACCEPTED',decision_at=#{decisionAt} WHERE id=#{id} AND outcome='PENDING' AND ended_at IS NULL") int acceptAssignment(AssignmentEntity a);
    @Insert("INSERT INTO repair_round(id,created_at,work_order_id,round_no,technician_id,started_at) VALUES(#{id},#{createdAt},#{workOrderId},#{roundNo},#{technicianId},#{startedAt})") int insertRound(RepairRoundEntity r);
    @Insert("INSERT INTO repair_progress(id,created_at,work_order_id,round_id,technician_id,content,recorded_at) VALUES(#{id},#{createdAt},#{workOrderId},#{roundId},#{technicianId},#{content},#{recordedAt})") int insertProgress(ProgressEntity p);
    @Update("UPDATE repair_round SET submitted_at=#{submittedAt},result_text=#{resultText} WHERE id=#{id} AND submitted_at IS NULL") int submitRound(RepairRoundEntity r);
    @Update("UPDATE repair_round SET acceptance_passed=#{acceptancePassed},acceptance_reason=#{acceptanceReason},accepted_by=#{acceptedBy},acceptance_at=#{acceptanceAt} WHERE id=#{id} AND acceptance_at IS NULL AND submitted_at IS NOT NULL") int acceptRound(RepairRoundEntity r);
    @Insert("INSERT INTO work_order_event(id,created_at,work_order_id,actor_user_id,assignment_id,round_id,progress_id,event_type,from_status,to_status,reason,occurred_at,operation_type,operation_id,content_digest,result_resource_id,result_version,result_round) VALUES(#{id},#{createdAt},#{workOrderId},#{actorUserId},#{assignmentId},#{roundId},#{progressId},#{eventType},#{fromStatus},#{toStatus},#{reason},#{occurredAt},#{operationType},#{operationId},#{contentDigest},#{resultResourceId},#{resultVersion},#{resultRound})") int insertEvent(WorkOrderEventEntity e);
}
