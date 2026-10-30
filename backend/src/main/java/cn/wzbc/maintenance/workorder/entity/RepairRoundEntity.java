package cn.wzbc.maintenance.workorder.entity;
import com.baomidou.mybatisplus.annotation.*;
import java.time.Instant;
@TableName("repair_round")
public class RepairRoundEntity {
    @TableId public Long id;
    public Long workOrderId,technicianId,acceptedBy;
    public int roundNo;
    public Instant createdAt,startedAt,submittedAt,acceptanceAt;
    public String resultText,acceptanceReason;
    public Boolean acceptancePassed;
}
