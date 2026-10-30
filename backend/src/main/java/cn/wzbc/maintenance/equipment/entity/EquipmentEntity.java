package cn.wzbc.maintenance.equipment.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

@TableName("equipment")
public class EquipmentEntity {
    @TableId public Long id;
    public Instant createdAt;
    public String equipmentNo;
    public String name;
    public Long locationId;
    public String addressText;
    public String facilityType;
    public boolean enabled;
    public String initializationKey;
    public int version;
    public Instant updatedAt;
}
