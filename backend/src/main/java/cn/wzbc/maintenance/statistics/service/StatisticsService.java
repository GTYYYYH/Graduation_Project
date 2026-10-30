package cn.wzbc.maintenance.statistics.service;

import cn.wzbc.maintenance.common.PageResult;
import cn.wzbc.maintenance.account.vo.CurrentUserVo;
import cn.wzbc.maintenance.repairrequest.vo.RepairRequestDetailVo.EquipmentReferenceVo;
import cn.wzbc.maintenance.security.*;
import cn.wzbc.maintenance.statistics.mapper.StatisticsMapper;
import cn.wzbc.maintenance.statistics.mapper.StatisticsMapper.*;
import cn.wzbc.maintenance.statistics.vo.StatisticsVo;
import cn.wzbc.maintenance.statistics.vo.StatisticsVo.*;
import java.math.*;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.*;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static cn.wzbc.maintenance.common.OperationResultService.*;

@Service
public class StatisticsService {
    private static final ZoneId SHANGHAI=ZoneId.of("Asia/Shanghai");
    private static final List<String> STATES=List.of("PENDING_ACCEPT","REASSIGN_PENDING","IN_PROGRESS","PENDING_ACCEPTANCE","COMPLETED");
    private static final Set<String> METRICS=Set.of("COMPLETED","RESPONSE_DURATION","REPAIR_DURATION","DAILY_COMPLETED","EQUIPMENT_FREQUENCY","CURRENT_STATE");
    private final StatisticsMapper mapper;
    private final BusinessAccessPolicy access;
    private final Clock clock;
    public StatisticsService(StatisticsMapper mapper,BusinessAccessPolicy access,Clock clock) { this.mapper=mapper;this.access=access;this.clock=clock; }

    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public StatisticsVo dashboard(CurrentUser actor,String month) {
        access.requireUser(actor,Set.of(CurrentUser.Role.ADMIN),false);
        var selected=month(month); var completed=mapper.rows(filter("MONTH_COMPLETED",selected));
        var samples=completed.stream().map(this::sample).toList();
        long invalid=samples.stream().filter(Sample::invalid).count();
        if(invalid>0)LoggerFactory.getLogger(getClass()).warn("Statistics time quality: month={}, invalidTimeSampleCount={}",month,invalid);
        var daily=new LinkedHashMap<String,Long>();
        for(int day=1;day<=selected.lengthOfMonth();day++)daily.put(selected.atDay(day).toString(),0L);
        for(var row:completed)daily.merge(row.completedAt.atZone(SHANGHAI).toLocalDate().toString(),1L,Long::sum);
        var counts=new HashMap<Long,Long>(); var references=new HashMap<Long,EquipmentReferenceVo>();
        for(var row:mapper.rows(filter("MONTH_CREATED",selected))) { counts.merge(row.equipmentId,1L,Long::sum);references.put(row.equipmentId,equipment(row)); }
        var frequencies=counts.entrySet().stream().sorted(Comparator.<Map.Entry<Long,Long>>comparingLong(Map.Entry::getValue).reversed()
            .thenComparing(Map.Entry::getKey,Comparator.nullsLast(Comparator.naturalOrder())))
            .map(e->new EquipmentFrequencyVo(references.get(e.getKey()),e.getValue())).toList();
        var states=new LinkedHashMap<String,Long>();STATES.forEach(s->states.put(s,0L));
        for(var state:mapper.stateCounts())states.put(state.status,state.count);
        return new StatisticsVo(month,"Asia/Shanghai",completed.size(),completed.size(),average(samples,true),average(samples,false),invalid,
            daily.entrySet().stream().map(e->new DailyCompletedVo(e.getKey(),e.getValue())).toList(),frequencies,"ALL_CURRENT_STATE",states,CurrentUserVo.instant(clock.instant()));
    }

    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public StatisticsDetailVo details(CurrentUser actor,String metric,String month,String date,String equipmentId,String unlinkedEquipment,String status,int page,int size,String keyword) {
        access.requireUser(actor,Set.of(CurrentUser.Role.ADMIN),false);page(page,size,keyword);
        if(metric==null || !METRICS.contains(metric))throw invalidQuery("统计指标不合法");
        String scope;YearMonth selected=null;Instant dateStart=null,dateEnd=null;Long equipment=null;boolean unlinked=false;
        if(metric.equals("CURRENT_STATE")) {
            if(month!=null || date!=null || equipmentId!=null || unlinkedEquipment!=null)throw invalidQuery("全部当前状态不接受月份或设备条件");
            if(status!=null && !STATES.contains(status))throw invalidQuery("工单状态不合法");
            scope="ALL_CURRENT_STATE";
        } else {
            selected=month(month);scope=metric.equals("EQUIPMENT_FREQUENCY")?"MONTH_CREATED":"MONTH_COMPLETED";
            if(status!=null)throw invalidQuery("月度指标不接受当前状态条件");
            if(metric.equals("DAILY_COMPLETED")) {
                try { var day=LocalDate.parse(Objects.requireNonNull(date));if(!YearMonth.from(day).equals(selected))throw invalidQuery("日期必须属于所选月份");dateStart=day.atStartOfDay(SHANGHAI).toInstant();dateEnd=day.plusDays(1).atStartOfDay(SHANGHAI).toInstant(); }
                catch(DateTimeParseException|NullPointerException e){throw invalidQuery("完成日期不合法");}
            } else if(date!=null)throw invalidQuery("此指标不接受日期条件");
            if(metric.equals("EQUIPMENT_FREQUENCY")) {
                if(equipmentId!=null && unlinkedEquipment==null)equipment=id(equipmentId);
                else if(equipmentId==null && "true".equals(unlinkedEquipment))unlinked=true;
                else throw invalidQuery("请选择具体设备或未关联台账分组");
            } else if(equipmentId!=null || unlinkedEquipment!=null)throw invalidQuery("此指标不接受设备条件");
        }
        var query=new Filter(scope,selected==null?null:start(selected),selected==null?null:start(selected.plusMonths(1)),dateStart,dateEnd,equipment,unlinked,status,keyword);
        var rows=mapper.rows(query);int from=(int)Math.min((long)(page-1)*size,rows.size()),to=Math.min(from+size,rows.size());
        var records=rows.subList(from,to).stream().map(row->{var s=sample(row);return new WorkOrderStatisticVo(row.id.toString(),row.workOrderNo,row.reportedEquipmentName,equipment(row),row.status,
            CurrentUserVo.instant(row.firstSubmittedAt),CurrentUserVo.instant(row.createdAt),CurrentUserVo.instant(row.acceptedAt),CurrentUserVo.instant(row.completedAt),seconds(s.responseMillis),seconds(s.repairMillis),s.invalid());}).toList();
        return new StatisticsDetailVo(metric,scope.equals("ALL_CURRENT_STATE")?null:month,scope,new PageResult<>(records,rows.size(),page,size),CurrentUserVo.instant(clock.instant()));
    }
    private YearMonth month(String value) {
        if(value==null || !value.matches("[0-9]{4}-(0[1-9]|1[0-2])"))throw invalidQuery("月份格式必须为YYYY-MM");
        try {var result=YearMonth.parse(value);if(result.getYear()<1000 || result.getYear()>9998)throw invalidQuery("月份超出数据库时间范围");return result;}
        catch(DateTimeParseException e){throw invalidQuery("月份不合法");}
    }
    private Instant start(YearMonth month){return month.atDay(1).atStartOfDay(SHANGHAI).toInstant();}
    private Filter filter(String scope,YearMonth month){return new Filter(scope,start(month),start(month.plusMonths(1)),null,null,null,false,null,null);}
    private RuntimeException invalidQuery(String message){return error(400,"INVALID_QUERY",message);}
    private EquipmentReferenceVo equipment(Row r){return r.equipmentId==null?null:new EquipmentReferenceVo(r.equipmentId.toString(),r.equipmentNo,r.equipmentName,r.equipmentLocationId==null?null:r.equipmentLocationId.toString());}
    private record Sample(BigDecimal responseMillis,BigDecimal repairMillis,boolean responseInvalid,boolean repairInvalid) {
        boolean invalid(){return responseInvalid||repairInvalid;}
    }
    private Sample sample(Row r) {
        boolean completed="COMPLETED".equals(r.status);
        boolean responseInvalid=r.firstSubmittedAt==null || r.createdAt==null || (r.firstSubmittedAt!=null && r.createdAt!=null && r.createdAt.isBefore(r.firstSubmittedAt))
            || (r.acceptedAt!=null && (r.firstSubmittedAt==null || r.acceptedAt.isBefore(r.firstSubmittedAt) || r.acceptedAt.isBefore(r.createdAt)))
            || (completed && (r.acceptedAt==null || r.completedAt==null || r.completedAt.isBefore(r.acceptedAt)));
        boolean repairInvalid=r.badRoundCount>0 || r.roundCount!=r.currentRound || (r.acceptedAt==null && r.roundCount>0)
            || (r.acceptedAt!=null && (r.roundCount==0 || r.firstRoundStartedAt==null || r.firstRoundStartedAt.isBefore(r.acceptedAt)))
            || (completed && (r.roundCount==0 || r.submittedCount!=r.roundCount || r.lastRoundSubmittedAt==null || r.completedAt==null || (r.lastRoundSubmittedAt!=null && r.completedAt.isBefore(r.lastRoundSubmittedAt))));
        BigDecimal response=responseInvalid || r.acceptedAt==null?null:BigDecimal.valueOf(Duration.between(r.firstSubmittedAt,r.acceptedAt).toMillis());
        BigDecimal repair=repairInvalid || r.roundCount==0 || r.submittedCount!=r.roundCount?null:r.repairMillis;
        return new Sample(response,repair,responseInvalid,repairInvalid);
    }
    private BigDecimal seconds(BigDecimal millis){return millis==null?null:millis.divide(BigDecimal.valueOf(1000),3,RoundingMode.HALF_UP);}
    private BigDecimal average(List<Sample> samples,boolean response) {
        if(samples.isEmpty() || samples.stream().anyMatch(s->response?s.responseInvalid:s.repairInvalid))return null;
        BigDecimal sum=BigDecimal.ZERO;for(var s:samples)sum=sum.add(response?s.responseMillis:s.repairMillis);
        return sum.divide(BigDecimal.valueOf(samples.size()).multiply(BigDecimal.valueOf(1000)),3,RoundingMode.HALF_UP);
    }
}
