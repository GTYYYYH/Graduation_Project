package cn.wzbc.maintenance.campus.entity;

import java.time.Instant;
public class CampusBuildingEntity {
    public Long id;
    public Instant createdAt;
    public String code;
    public String name;
    public String type;
    public int displayOrder;
    public String configVersion;
}
