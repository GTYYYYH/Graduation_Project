package cn.wzbc.maintenance.campus.vo;

import java.util.List;
public record CatalogVo(String configVersion, List<BuildingVo> buildings, List<FloorVo> floors, List<LocationVo> locations) {
    public record BuildingVo(String id, String code, String name, String type, int displayOrder) {}
    public record FloorVo(String id, String code, String buildingId, int level, String name, int displayOrder) {}
    public record LocationVo(String id, String code, String floorId, String type, String use, String name,
            boolean reportable, boolean canCreateRequest, int displayOrder) {}
}
