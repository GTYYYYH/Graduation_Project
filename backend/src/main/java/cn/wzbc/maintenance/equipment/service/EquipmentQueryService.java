package cn.wzbc.maintenance.equipment.service;
import cn.wzbc.maintenance.equipment.entity.EquipmentEntity;
import cn.wzbc.maintenance.equipment.mapper.EquipmentMapper;
import cn.wzbc.maintenance.equipment.vo.EquipmentVo;
import cn.wzbc.maintenance.workorder.service.WorkOrderQueryService;
import cn.wzbc.maintenance.workorder.vo.WorkOrderDetailVo.SummaryVo;
import cn.wzbc.maintenance.common.PageResult;
import cn.wzbc.maintenance.security.*;
import java.time.Clock;
import java.util.*;
import cn.wzbc.maintenance.account.vo.CurrentUserVo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static cn.wzbc.maintenance.common.OperationResultService.*;
@Service
public class EquipmentQueryService {
    public record EquipmentHistoryQuery(int page,int pageSize,String keyword,String status) {}
    private final EquipmentMapper equipment; private final WorkOrderQueryService orders; private final BusinessAccessPolicy access; private final Clock clock;
    public EquipmentQueryService(EquipmentMapper equipment,WorkOrderQueryService orders,BusinessAccessPolicy access,Clock clock) { this.equipment=equipment; this.orders=orders; this.access=access; this.clock=clock; }
    public EquipmentVo value(EquipmentEntity e,CurrentUser actor) {
        access.requireUser(actor,Set.of(CurrentUser.Role.ADMIN),false); var current=orders.list(actor,1,1,null,null,"CURRENT",null,null,e.id);
        return new EquipmentVo(e.id.toString(),e.equipmentNo,e.name,e.locationId==null?null:e.locationId.toString(),e.addressText,e.facilityType,e.enabled,e.version,current.records().isEmpty()?null:current.records().getFirst(),List.of("UPDATE_EQUIPMENT",e.enabled?"DISABLE_EQUIPMENT":"ENABLE_EQUIPMENT","VIEW_EQUIPMENT_HISTORY"));
    }
    @Transactional(readOnly=true)
    public EquipmentVo.DetailVo currentAndHistory(long id,EquipmentHistoryQuery query,CurrentUser actor) {
        access.requireUser(actor,Set.of(CurrentUser.Role.ADMIN),false); var e=equipment.selectById(id); if(e==null) throw error(404,"RESOURCE_NOT_FOUND","设备不存在");
        return new EquipmentVo.DetailVo(value(e,actor),history(id,query,actor),CurrentUserVo.instant(clock.instant()));
    }
    public PageResult<SummaryVo> history(long id,EquipmentHistoryQuery query,CurrentUser actor) { access.requireUser(actor,Set.of(CurrentUser.Role.ADMIN),false); return orders.list(actor,query.page(),query.pageSize(),query.keyword(),query.status(),"HISTORY",null,null,id); }
}
