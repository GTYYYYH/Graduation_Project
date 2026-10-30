package cn.wzbc.maintenance.equipment.service;
import cn.wzbc.maintenance.equipment.dto.*;
import cn.wzbc.maintenance.equipment.entity.EquipmentEntity;
import cn.wzbc.maintenance.equipment.mapper.EquipmentMapper;
import cn.wzbc.maintenance.equipment.vo.EquipmentVo;
import cn.wzbc.maintenance.repairrequest.mapper.RepairRequestMapper;
import cn.wzbc.maintenance.security.*;
import cn.wzbc.maintenance.common.*;
import cn.wzbc.maintenance.account.vo.CurrentUserVo;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static cn.wzbc.maintenance.common.OperationResultService.*;
@Service
public class EquipmentService {
    private static final Map<String,Set<String>> ALLOWED=Map.of(
        "DORM_ROOM",Set.of("BUNK_DESK","TOILET","SHOWER","BASIN","AIR_CONDITIONER","LIGHT_GROUP","SOCKET_GROUP"),
        "CLASSROOM",Set.of("CLASS_DESK_CHAIR_GROUP","AIR_CONDITIONER_GROUP","LIGHT_GROUP","SOCKET_GROUP","COMPUTER","PROJECTOR"),
        "OFFICE",Set.of("OFFICE_DESK_CHAIR_GROUP","AIR_CONDITIONER","LIGHT_GROUP","SOCKET_GROUP"),
        "CANTEEN_AREA",Set.of("STANDING_AC_GROUP","LIGHT_GROUP"),"GYM_GALLERY",Set.of("STANDING_AC_GROUP","LIGHT_GROUP"),"GYM_COURT",Set.of("STANDING_AC_GROUP","LIGHT_GROUP","BASKETBALL_HOOP_GROUP"));
    private final EquipmentMapper equipment; private final RepairRequestMapper locations; private final EquipmentQueryService query; private final BusinessAccessPolicy access; private final OperationResultService operations; private final Clock clock;
    public EquipmentService(EquipmentMapper equipment,RepairRequestMapper locations,EquipmentQueryService query,BusinessAccessPolicy access,OperationResultService operations,Clock clock) { this.equipment=equipment; this.locations=locations; this.query=query; this.access=access; this.operations=operations; this.clock=clock; }
    private void facility(Long location,String type) { if(location==null) throw error(400,"INVALID_LOCATION","请明确选择设备的范围内位置"); var l=locations.location(location); if(l==null || !l.reportable) throw error(400,"INVALID_LOCATION","设备位置必须是范围内可报修空间"); if(!ALLOWED.getOrDefault(l.spaceUse,Set.of()).contains(type)) throw error(400,"FACILITY_OUT_OF_SCOPE","该空间不允许此设施类型"); }
    /** Caller locks participating users first; this method locks the same equipment used by maintenance. */
    public EquipmentEntity lockAndValidateEquipment(long id,Long location,CurrentUser actor) {
        access.requireUser(actor,Set.of(CurrentUser.Role.ADMIN),false); var e=equipment.lock(id); if(e==null) throw error(404,"RESOURCE_NOT_FOUND","设备不存在");
        if(!e.enabled) throw error(409,"EQUIPMENT_DISABLED","停用设备不能受理新工单"); validateReviewMatch(e,location); return e;
    }
    /** Same fixed scope for approval and concrete duplicate evidence; legacy text is never rebound. */
    public void validateReviewMatch(EquipmentEntity e,Long requestLocation) {
        if(requestLocation!=null && e.locationId==null) throw error(409,"LOCATION_MISMATCH","台账缺少结构化位置，请先维护设备位置后返回审核");
        if(e.locationId==null) { if(ALLOWED.values().stream().noneMatch(types -> types.contains(e.facilityType))) throw error(400,"OUT_OF_SCOPE_EQUIPMENT","该设施不在首版报修范围，请退回纠正或撤回"); }
        else { var location=locations.location(e.locationId); if(location==null || !location.reportable || !ALLOWED.getOrDefault(location.spaceUse,Set.of()).contains(e.facilityType)) throw error(400,"OUT_OF_SCOPE_EQUIPMENT","该位置不允许此设施，请退回纠正或撤回"); }
        if(requestLocation!=null && !requestLocation.equals(e.locationId)) throw error(409,"LOCATION_MISMATCH","申请位置："+locations.locationName(requestLocation)+"；台账位置："+locations.locationName(e.locationId)+"。位置不一致，请退回原申请纠正或重新匹配台账");
    }
    @Transactional
    public Receipt create(CurrentUser actor,EquipmentSaveDto dto) {
        access.requireUser(actor,Set.of(CurrentUser.Role.ADMIN),true); uuid(dto.operationId()); if(dto.version()!=null) throw error(400,"VALIDATION_FAILED","新增设备不能提供版本"); long location=id(dto.locationId()); text(dto.name(),100,"设备名称"); text(dto.addressText(),500,"台账地址"); facility(location,dto.facilityType()); String reason=reason(dto.reason());
        byte[] digest=operations.digest(Arrays.asList("EQUIPMENT_CREATE",null,null,null,dto.name(),location,dto.addressText(),dto.facilityType(),reason)); var replay=operations.replay(actor,"EQUIPMENT_CREATE",dto.operationId(),digest); if(replay!=null) return replay;
        Instant now=clock.instant().truncatedTo(ChronoUnit.MILLIS); var e=new EquipmentEntity(); e.id=IdWorker.getId(); e.createdAt=now; e.updatedAt=now; e.equipmentNo="EQ-"+e.id; e.name=dto.name(); e.locationId=location; e.addressText=dto.addressText(); e.facilityType=dto.facilityType(); e.enabled=true;
        equipment.insert(e); event(e,actor,"CREATED","EQUIPMENT_CREATE",dto.operationId(),digest,now); return receipt(e,dto.operationId(),now);
    }
    @Transactional
    public Receipt update(CurrentUser actor,long id,EquipmentSaveDto dto) {
        access.requireUser(actor,Set.of(CurrentUser.Role.ADMIN),true); uuid(dto.operationId()); long location=id(dto.locationId()); text(dto.name(),100,"设备名称"); text(dto.addressText(),500,"台账地址"); String reason=reason(dto.reason());
        byte[] digest=operations.digest(Arrays.asList("EQUIPMENT_UPDATE",id,dto.version(),null,dto.name(),location,dto.addressText(),dto.facilityType(),reason)); var replay=operations.replay(actor,"EQUIPMENT_UPDATE",dto.operationId(),digest); if(replay!=null) return replay;
        var e=equipment.lock(id); if(e==null) throw error(404,"RESOURCE_NOT_FOUND","设备不存在"); version(e.version,dto.version()); facility(location,dto.facilityType()); boolean move=!Objects.equals(location,e.locationId);
        if(move && equipment.unfinished(id)>0) throw error(409,"EQUIPMENT_MOVE_BLOCKED","该设备有未完结工单，不能移动位置"); if(move && reason==null) throw error(400,"VALIDATION_FAILED","移动设备必须填写原因");
        Instant now=clock.instant().truncatedTo(ChronoUnit.MILLIS); Long oldLocation=e.locationId; String oldAddress=e.addressText;
        if(equipment.update(null,new UpdateWrapper<EquipmentEntity>().eq("id",id).eq("version",e.version).set("name",dto.name()).set("location_id",location).set("address_text",dto.addressText()).set("facility_type",dto.facilityType()).set("version",e.version+1).set("updated_at",now))!=1) throw error(409,"VERSION_CONFLICT","设备资料已变化，请重新核对");
        e.version++; long event=event(e,actor,move?"LOCATION_CHANGED":"UPDATED","EQUIPMENT_UPDATE",dto.operationId(),digest,now);
        if(move) equipment.locationHistory(IdWorker.getId(),id,actor.id(),oldLocation,location,oldAddress,dto.addressText(),reason,event,now); return receipt(e,dto.operationId(),now);
    }
    @Transactional
    public Receipt status(CurrentUser actor,long id,EquipmentStatusDto dto,boolean enabled) {
        access.requireUser(actor,Set.of(CurrentUser.Role.ADMIN),true); uuid(dto.operationId()); String type=enabled?"EQUIPMENT_ENABLE":"EQUIPMENT_DISABLE";
        byte[] digest=operations.digest(Arrays.asList(type,id,dto.version(),null)); var replay=operations.replay(actor,type,dto.operationId(),digest); if(replay!=null) return replay;
        var e=equipment.lock(id); if(e==null) throw error(404,"RESOURCE_NOT_FOUND","设备不存在"); version(e.version,dto.version()); if(e.enabled==enabled) throw error(409,"STATE_CONFLICT","设备状态已变化，请重新核对");
        if(enabled) facility(e.locationId,e.facilityType); Instant now=clock.instant().truncatedTo(ChronoUnit.MILLIS);
        if(equipment.update(null,new UpdateWrapper<EquipmentEntity>().eq("id",id).eq("version",e.version).set("enabled",enabled).set("version",e.version+1).set("updated_at",now))!=1) throw error(409,"VERSION_CONFLICT","设备状态已变化，请重新核对");
        e.version++; event(e,actor,enabled?"ENABLED":"DISABLED",type,dto.operationId(),digest,now); return receipt(e,dto.operationId(),now);
    }
    private static String reason(String value) { return value==null || value.isBlank()?null:text(value,2000,"移动原因"); }
    private long event(EquipmentEntity e,CurrentUser actor,String type,String operationType,String operation,byte[] digest,Instant now) { long id=IdWorker.getId(); equipment.event(id,e.id,actor.id(),type,operationType,operation,digest,e.version,now); return id; }
    private Receipt receipt(EquipmentEntity e,String operation,Instant now) { return new Receipt(operation,"EQUIPMENT",e.id.toString(),e.version,null,CurrentUserVo.instant(now),null,null); }
    @Transactional(readOnly=true)
    public PageResult<EquipmentVo> list(CurrentUser actor,int page,int size,String keyword,String building,String floor,String location,Boolean enabled,String type) {
        access.requireUser(actor,Set.of(CurrentUser.Role.ADMIN),false); page(page,size,keyword); var q=new QueryWrapper<EquipmentEntity>();
        if(building!=null && !building.isBlank()) q.apply("EXISTS(SELECT 1 FROM campus_location l JOIN campus_floor f ON f.id=l.floor_id WHERE l.id=equipment.location_id AND f.building_id={0})",id(building));
        if(floor!=null && !floor.isBlank()) q.apply("EXISTS(SELECT 1 FROM campus_location l WHERE l.id=equipment.location_id AND l.floor_id={0})",id(floor));
        if(location!=null && !location.isBlank()) q.eq("location_id",id(location)); if(enabled!=null) q.eq("enabled",enabled);
        if(type!=null && !type.isBlank()) { if(ALLOWED.values().stream().noneMatch(types -> types.contains(type))) throw error(400,"INVALID_QUERY","设施类型不合法"); q.eq("facility_type",type); }
        if(keyword!=null && !keyword.isBlank()) q.and(p -> p.apply("CONVERT(equipment_no USING utf8mb4) LIKE {0}","%"+keyword+"%").or().like("name",keyword).or().like("address_text",keyword));
        long count=equipment.selectCount(q); q.orderByDesc("created_at","id").last("LIMIT "+size+" OFFSET "+((long)(page-1)*size)); return new PageResult<>(equipment.selectList(q).stream().map(e -> query.value(e,actor)).toList(),count,page,size);
    }
}
