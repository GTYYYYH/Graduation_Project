package cn.wzbc.maintenance.repairrequest.entity;
import com.baomidou.mybatisplus.annotation.*;
import java.time.Instant;
@TableName("repair_request")
public class RepairRequestEntity {
    @TableId public Long id;
    public Instant createdAt;
    public String requestNo;
    public Long reporterId;
    public String equipmentName,equipmentCategory,addressText,description,contactName,contactPhone,locationMode;
    public Long locationId;
    public String locationNameSnapshot,status;
    public Instant firstSubmittedAt;
    public Long currentSubmissionId,matchedEquipmentId;
    public int version;
    public Instant updatedAt;
}
