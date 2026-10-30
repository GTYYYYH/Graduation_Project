package cn.wzbc.maintenance.equipment.vo;
import cn.wzbc.maintenance.workorder.vo.WorkOrderDetailVo.SummaryVo;
import cn.wzbc.maintenance.common.PageResult;
import java.util.List;
public record EquipmentVo(String id,String equipmentNo,String name,String locationId,String addressText,String facilityType,boolean enabled,int version,SummaryVo currentWorkOrder,List<String> allowedActions) {
    public record DetailVo(EquipmentVo equipment,PageResult<SummaryVo> history,String queriedAt) {}
}
