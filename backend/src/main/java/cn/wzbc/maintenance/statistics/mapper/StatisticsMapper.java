package cn.wzbc.maintenance.statistics.mapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface StatisticsMapper {
    record Filter(String scope,Instant start,Instant end,Instant dateStart,Instant dateEnd,
            Long equipmentId,boolean unlinked,String status,String keyword) {}
    class Row {
        public Long id,equipmentId,equipmentLocationId;
        public String workOrderNo,reportedEquipmentName,status,equipmentNo,equipmentName;
        public Instant firstSubmittedAt,createdAt,acceptedAt,completedAt,firstRoundStartedAt,lastRoundSubmittedAt;
        public int currentRound;
        public long roundCount,submittedCount,badRoundCount;
        public BigDecimal repairMillis;
    }
    class StateCount { public String status; public long count; }
    List<Row> rows(Filter filter);
    List<StateCount> stateCounts();
}
