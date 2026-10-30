package cn.wzbc.maintenance.statistics.vo;

import cn.wzbc.maintenance.common.PageResult;
import cn.wzbc.maintenance.repairrequest.vo.RepairRequestDetailVo.EquipmentReferenceVo;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record StatisticsVo(String month,String timeZone,long completedCount,long sampleCount,
        BigDecimal averageResponseSeconds,BigDecimal averageRepairSeconds,long invalidTimeSampleCount,
        List<DailyCompletedVo> dailyCompleted,List<EquipmentFrequencyVo> equipmentFrequencies,
        String stateScope,Map<String,Long> stateCounts,String queriedAt) {
    public record DailyCompletedVo(String date,long count) {}
    /** null equipment is one explicit unlinked bucket, never a guessed asset identity. */
    public record EquipmentFrequencyVo(EquipmentReferenceVo equipment,long count) {}
    public record WorkOrderStatisticVo(String id,String workOrderNo,String reportedEquipmentName,
            EquipmentReferenceVo equipment,String status,String firstSubmittedAt,String createdAt,
            String acceptedAt,String completedAt,BigDecimal responseSeconds,BigDecimal repairSeconds,boolean hasInvalidTime) {}
    public record StatisticsDetailVo(String metric,String month,String scope,
            PageResult<WorkOrderStatisticVo> records,String queriedAt) {}
}
