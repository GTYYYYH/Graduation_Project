package cn.wzbc.maintenance.campus.entity;

import java.time.Instant;
public class CampusLocationEntity {
    public Long id;
    public Instant createdAt;
    public Long floorId;
    public String code;
    public String type;
    public String spaceUse;
    public String name;
    public boolean reportable;
    public int displayOrder;
    public String configVersion;
    public int version;
    public Instant updatedAt;
}
