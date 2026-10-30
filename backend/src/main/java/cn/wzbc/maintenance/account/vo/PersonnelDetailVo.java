package cn.wzbc.maintenance.account.vo;
import cn.wzbc.maintenance.common.PageResult;
import java.util.*;
public record PersonnelDetailVo(PersonnelVo person,List<String> availableCategories,Map<String,Long> categoryTotals,Map<String,Long> statusCounts,PageResult<BusinessHistoryItemVo> records,String queriedAt) {
    public record BusinessHistoryItemVo(String resourceType,String id,String businessNo,String equipmentName,String status,String createdAt,List<String> relations,boolean canViewDetail) {}
}
