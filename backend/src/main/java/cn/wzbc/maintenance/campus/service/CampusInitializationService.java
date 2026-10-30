package cn.wzbc.maintenance.campus.service;

import cn.wzbc.maintenance.campus.entity.*;
import cn.wzbc.maintenance.campus.mapper.CampusMapper;
import cn.wzbc.maintenance.equipment.entity.EquipmentEntity;
import cn.wzbc.maintenance.equipment.mapper.EquipmentMapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class CampusInitializationService {
    private final CampusMapper campus;
    private final EquipmentMapper equipment;
    private final ObjectMapper json;
    private final Clock clock;
    public CampusInitializationService(CampusMapper campus, EquipmentMapper equipment, ObjectMapper json, Clock clock) {
        this.campus=campus; this.equipment=equipment; this.json=json; this.clock=clock;
    }
    public record Configuration(String configVersion, boolean demonstration, List<Building> buildings,
            List<Floor> floors, List<Location> locations, List<Item> equipment) {}
    public record Building(String code, String name, String type, int displayOrder) {}
    public record Floor(String code, String buildingCode, int level, String name, int displayOrder) {}
    public record Location(String code, String floorCode, String type, String use, String name, boolean reportable, int displayOrder) {}
    public record Item(String initializationKey, String equipmentNo, String locationCode, String facilityType, String name, String addressText) {}

    private static final Map<String,List<String>> FACILITIES = Map.of(
        "DORM_ROOM", List.of("BUNK_DESK","BUNK_DESK","TOILET","SHOWER","BASIN","AIR_CONDITIONER","LIGHT_GROUP","SOCKET_GROUP"),
        "CLASSROOM", List.of("CLASS_DESK_CHAIR_GROUP","AIR_CONDITIONER_GROUP","LIGHT_GROUP","SOCKET_GROUP","COMPUTER","PROJECTOR"),
        "OFFICE", List.of("OFFICE_DESK_CHAIR_GROUP","AIR_CONDITIONER","LIGHT_GROUP","SOCKET_GROUP"),
        "CANTEEN_AREA", List.of("STANDING_AC_GROUP","LIGHT_GROUP"),
        "GYM_COURT", List.of("BASKETBALL_HOOP_GROUP","LIGHT_GROUP","STANDING_AC_GROUP"),
        "GYM_GALLERY", List.of("LIGHT_GROUP","STANDING_AC_GROUP"));

    @Transactional
    public boolean initialize(Path configuration) {
        final Configuration cfg;
        final byte[] digest;
        try {
            byte[] content=Files.readAllBytes(configuration);
            cfg=json.readValue(content, Configuration.class);
            digest=MessageDigest.getInstance("SHA-256").digest(content);
        } catch (Exception error) { throw new IllegalStateException("校园配置不可读取或格式错误"); }
        validate(cfg);
        var snapshot=campus.initialization(cfg.configVersion());
        byte[] saved=snapshot==null ? null : snapshot.configDigest;
        if (saved!=null && !MessageDigest.isEqual(saved,digest)) conflict();
        var buildings=index(campus.buildings(), b -> b.code);
        var floors=index(campus.floors(), f -> f.code);
        var locations=index(campus.locations(), l -> l.code);
        var items=equipment.selectList(null);
        var initialized=index(items.stream().filter(e -> e.initializationKey!=null).toList(), e -> e.initializationKey);
        var configuredBuildings=index(cfg.buildings(), Building::code);
        var configuredFloors=index(cfg.floors(), Floor::code);
        var configuredLocations=index(cfg.locations(), Location::code);
        var configuredItems=index(cfg.equipment(), Item::initializationKey);
        // 导入前逐项核对身份与不变量。当前设备资料和位置可合法变化，不据初始值覆盖。
        for (var existing:buildings.values()) {
            var expected=configuredBuildings.get(existing.code);
            if (expected==null || !existing.configVersion.equals(cfg.configVersion()) || !existing.type.equals(expected.type())) conflict();
        }
        for (var existing:floors.values()) {
            var expected=configuredFloors.get(existing.code);
            var parent=expected==null ? null : buildings.get(expected.buildingCode());
            if (parent==null || !existing.configVersion.equals(cfg.configVersion()) || !existing.buildingId.equals(parent.id) || existing.level!=expected.level()) conflict();
        }
        for (var existing:locations.values()) {
            var expected=configuredLocations.get(existing.code);
            var parent=expected==null ? null : floors.get(expected.floorCode());
            if (parent==null || !existing.configVersion.equals(cfg.configVersion()) || !existing.floorId.equals(parent.id)
                    || !existing.type.equals(expected.type()) || !existing.spaceUse.equals(expected.use()) || existing.reportable!=expected.reportable()) conflict();
        }
        for (var existing:initialized.values()) {
            var expected=configuredItems.get(existing.initializationKey);
            if (expected==null || !existing.equipmentNo.equals(expected.equipmentNo())) conflict();
        }
        if (saved==null && (!buildings.isEmpty() || !floors.isEmpty() || !locations.isEmpty() || !items.isEmpty())) {
            throw new IllegalStateException("已有空间或设备而无本配置初始化记录，拒绝接管");
        }
        int writes=0;
        var now=clock.instant();
        for (var value:cfg.buildings()) if (!buildings.containsKey(value.code())) {
            var entity=new CampusBuildingEntity(); entity.id=IdWorker.getId(); entity.createdAt=now; entity.code=value.code();
            entity.name=value.name(); entity.type=value.type(); entity.displayOrder=value.displayOrder(); entity.configVersion=cfg.configVersion();
            campus.insertBuilding(entity); buildings.put(entity.code,entity); writes++;
        }
        for (var value:cfg.floors()) if (!floors.containsKey(value.code())) {
            var entity=new CampusFloorEntity(); entity.id=IdWorker.getId(); entity.createdAt=now; entity.code=value.code();
            entity.buildingId=buildings.get(value.buildingCode()).id; entity.level=value.level(); entity.name=value.name();
            entity.displayOrder=value.displayOrder(); entity.configVersion=cfg.configVersion();
            campus.insertFloor(entity); floors.put(entity.code,entity); writes++;
        }
        for (var value:cfg.locations()) if (!locations.containsKey(value.code())) {
            var entity=new CampusLocationEntity(); entity.id=IdWorker.getId(); entity.createdAt=now; entity.updatedAt=now;
            entity.code=value.code(); entity.floorId=floors.get(value.floorCode()).id; entity.type=value.type(); entity.spaceUse=value.use();
            entity.name=value.name(); entity.reportable=value.reportable(); entity.displayOrder=value.displayOrder(); entity.configVersion=cfg.configVersion();
            campus.insertLocation(entity); locations.put(entity.code,entity); writes++;
        }
        for (var value:cfg.equipment()) if (!initialized.containsKey(value.initializationKey())) {
            var entity=new EquipmentEntity(); entity.id=IdWorker.getId(); entity.createdAt=now; entity.updatedAt=now;
            entity.equipmentNo=value.equipmentNo(); entity.name=value.name(); entity.locationId=locations.get(value.locationCode()).id;
            entity.addressText=value.addressText(); entity.facilityType=value.facilityType(); entity.initializationKey=value.initializationKey(); entity.enabled=true;
            equipment.insert(entity); writes++;
        }
        if (saved==null) campus.recordInitialization(IdWorker.getId(), now, cfg.configVersion(), digest);
        return writes>0;
    }

    private static void validate(Configuration c) {
        if (c==null || !"campus-v1".equals(c.configVersion()) || !c.demonstration() || c.buildings()==null || c.floors()==null || c.locations()==null || c.equipment()==null
                || c.buildings().size()!=10 || c.floors().size()!=41 || c.locations().size()!=296 || c.equipment().size()!=1817) conflict();
        var bs=index(c.buildings(), Building::code); var fs=index(c.floors(), Floor::code); var ls=index(c.locations(), Location::code);
        index(c.equipment(), Item::initializationKey); index(c.equipment(), Item::equipmentNo);
        for (var b:c.buildings()) if (!code(b.code()) || !text(b.name(),100) || b.displayOrder()<0 || !Set.of("DORMITORY","TEACHING","CANTEEN","GYM").contains(b.type())) conflict();
        for (var f:c.floors()) if (!code(f.code()) || !bs.containsKey(f.buildingCode()) || f.level()<1 || f.displayOrder()<0 || !text(f.name(),100)) conflict();
        for (var l:c.locations()) if (!code(l.code()) || !fs.containsKey(l.floorCode()) || !text(l.name(),100) || l.displayOrder()<0
                || !Set.of("ROOM","AREA").contains(l.type()) || l.reportable()!=FACILITIES.containsKey(l.use())
                || (!l.reportable() && !Set.of("DORM_LOBBY","DORM_DUTY","TEACHING_LOBBY","RESTROOM_MALE","RESTROOM_FEMALE").contains(l.use()))) conflict();
        if (c.locations().stream().filter(Location::reportable).count()!=257) conflict();
        var facilities=new HashMap<String,List<String>>();
        for (var e:c.equipment()) {
            var l=ls.get(e.locationCode());
            if (!code(e.initializationKey()) || !code(e.equipmentNo()) || l==null || !l.reportable() || !text(e.name(),100) || !text(e.addressText(),500)) conflict();
            facilities.computeIfAbsent(e.locationCode(), key -> new ArrayList<>()).add(e.facilityType());
        }
        for (var l:c.locations()) {
            var actual=new ArrayList<>(facilities.getOrDefault(l.code(), List.of())); Collections.sort(actual);
            var expected=new ArrayList<>(FACILITIES.getOrDefault(l.use(),List.of())); Collections.sort(expected);
            if (!actual.equals(expected)) conflict();
        }
    }
    private static boolean code(String value) { return value!=null && value.matches("[A-Za-z0-9:_-]{1,64}"); }
    private static boolean text(String value,int max) { return value!=null && !value.isBlank() && value.codePointCount(0,value.length())<=max; }
    private static <T> Map<String,T> index(List<T> values,Function<T,String> key) {
        try { return values.stream().collect(Collectors.toMap(key,Function.identity(),(a,b) -> { conflict(); return a; },LinkedHashMap::new)); }
        catch (RuntimeException error) { throw new IllegalStateException("校园配置身份不合法或重复"); }
    }
    private static void conflict() { throw new IllegalStateException("校园配置与既有数据不兼容，未覆盖数据"); }
}
