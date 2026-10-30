package cn.wzbc.maintenance.workorder.entity;
import com.baomidou.mybatisplus.annotation.*;
import java.time.Instant;
@TableName("work_order_assignment")
public class AssignmentEntity {
    @TableId public Long id;
    public Long workOrderId,technicianId,assignedBy,visibleSubmissionId;
    public int assignmentNo;
    public Instant createdAt,assignedAt,endedAt,decisionAt;
    public String outcome,reason;
}
