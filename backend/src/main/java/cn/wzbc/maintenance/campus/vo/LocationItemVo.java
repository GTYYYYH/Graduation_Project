package cn.wzbc.maintenance.campus.vo;

import cn.wzbc.maintenance.common.PageResult;
import java.util.List;

public record LocationItemVo(String resourceType,String id,String businessNo,String equipmentName,String status,String stage,String createdAt,List<String> allowedActions) {
    public record LocationItemsVo(CatalogVo.LocationVo location,LocationSummaryVo.StageCounts counts,String category,long visibleTotal,PageResult<LocationItemVo> items,String queriedAt) {}
}
