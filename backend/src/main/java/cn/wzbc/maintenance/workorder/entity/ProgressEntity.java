package cn.wzbc.maintenance.workorder.entity;
import com.baomidou.mybatisplus.annotation.*;
import java.time.Instant;
@TableName("repair_progress")
public class ProgressEntity {
    @TableId public Long id;
    public Long workOrderId,roundId,technicianId;
    public String content;
    public Instant createdAt,recordedAt;
}
