package cn.wzbc.maintenance.campus.entity;

import java.time.Instant;
public class CampusFloorEntity {
    public Long id;
    public Instant createdAt;
    public Long buildingId;
    public int level;
    public String code;
    public String name;
    public int displayOrder;
    public String configVersion;
}
