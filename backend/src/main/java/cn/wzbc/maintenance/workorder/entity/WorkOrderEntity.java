package cn.wzbc.maintenance.workorder.entity;
import com.baomidou.mybatisplus.annotation.*;
import java.time.Instant;
@TableName("work_order")
public class WorkOrderEntity {
    @TableId public Long id;
    public Instant createdAt,updatedAt,acceptedAt,completedAt;
    public String workOrderNo,locationMode,locationNameSnapshot,status;
    public Long requestId,equipmentId,reporterId,locationId,currentAssignmentId,assignedTechnicianId,acceptedTechnicianId;
    public int version,currentRound;
}
