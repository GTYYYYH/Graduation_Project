package cn.wzbc.maintenance.campus.service;

import cn.wzbc.maintenance.campus.mapper.CampusMapper;
import cn.wzbc.maintenance.campus.vo.CatalogVo;
import cn.wzbc.maintenance.common.BusinessException;
import cn.wzbc.maintenance.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CampusCatalogService {
    private final CampusMapper mapper;
    public CampusCatalogService(CampusMapper mapper) { this.mapper = mapper; }

    @Transactional(readOnly = true)
    public CatalogVo catalog(CurrentUser user) {
        if (user == null || !user.enabled()) throw new BusinessException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "请先登录");
        var buildings = mapper.buildings().stream().map(b -> new CatalogVo.BuildingVo(b.id.toString(), b.code, b.name, b.type, b.displayOrder)).toList();
        var floors = mapper.floors().stream().map(f -> new CatalogVo.FloorVo(f.id.toString(), f.code, f.buildingId.toString(), f.level, f.name, f.displayOrder)).toList();
        var locations = mapper.locations().stream().map(l -> new CatalogVo.LocationVo(l.id.toString(), l.code, l.floorId.toString(), l.type,
                l.spaceUse, l.name, l.reportable, l.reportable && user.role() == CurrentUser.Role.REPORTER, l.displayOrder)).toList();
        String version = mapper.configVersion();
        return new CatalogVo(version == null ? "" : version, buildings, floors, locations);
    }
}
