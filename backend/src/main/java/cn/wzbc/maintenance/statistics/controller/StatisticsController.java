package cn.wzbc.maintenance.statistics.controller;

import cn.wzbc.maintenance.common.ApiResponse;
import cn.wzbc.maintenance.security.CurrentUser;
import cn.wzbc.maintenance.statistics.service.StatisticsService;
import cn.wzbc.maintenance.statistics.vo.StatisticsVo;
import cn.wzbc.maintenance.statistics.vo.StatisticsVo.StatisticsDetailVo;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import static cn.wzbc.maintenance.common.OperationResultService.*;

@RestController
public class StatisticsController {
    private final StatisticsService service;
    public StatisticsController(StatisticsService service){this.service=service;}
    private void query(HttpServletRequest request,Set<String> fields) {
        if(request.getParameterMap().entrySet().stream().anyMatch(p->!fields.contains(p.getKey()) || p.getValue().length!=1))throw error(400,"INVALID_QUERY","查询字段不合法");
    }
    @GetMapping("/api/statistics/dashboard")
    public ApiResponse<StatisticsVo> dashboard(@AuthenticationPrincipal CurrentUser actor,@RequestParam(required=false) String month,HttpServletRequest request) {
        query(request,Set.of("month"));return ApiResponse.success(service.dashboard(actor,month),request);
    }
    @GetMapping("/api/statistics/details")
    public ApiResponse<StatisticsDetailVo> details(@AuthenticationPrincipal CurrentUser actor,@RequestParam(required=false) String metric,
            @RequestParam(required=false) String month,@RequestParam(required=false) String date,@RequestParam(required=false) String equipmentId,
            @RequestParam(required=false) String unlinkedEquipment,@RequestParam(required=false) String status,@RequestParam(defaultValue="1") int page,
            @RequestParam(defaultValue="20") int pageSize,@RequestParam(required=false) String keyword,HttpServletRequest request) {
        query(request,Set.of("metric","month","date","equipmentId","unlinkedEquipment","status","page","pageSize","keyword"));
        return ApiResponse.success(service.details(actor,metric,month,date,equipmentId,unlinkedEquipment,status,page,pageSize,keyword),request);
    }
}
