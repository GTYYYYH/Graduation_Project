package cn.wzbc.maintenance.campus.controller;

import cn.wzbc.maintenance.campus.service.CampusSummaryService;
import cn.wzbc.maintenance.campus.vo.LocationSummaryVo.CampusSummariesVo;
import cn.wzbc.maintenance.campus.vo.LocationItemVo.LocationItemsVo;
import cn.wzbc.maintenance.common.ApiResponse;
import cn.wzbc.maintenance.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import static cn.wzbc.maintenance.common.OperationResultService.*;

@RestController @RequestMapping("/api/campus")
public class CampusBusinessController {
    private final CampusSummaryService service;
    public CampusBusinessController(CampusSummaryService service) { this.service=service; }
    private void query(HttpServletRequest request,Set<String> keys) {
        if(request.getParameterMap().entrySet().stream().anyMatch(p -> !keys.contains(p.getKey()) || p.getValue().length!=1)) throw error(400,"INVALID_QUERY","查询字段不合法");
    }
    @GetMapping("/summaries") public ApiResponse<CampusSummariesVo> summaries(@AuthenticationPrincipal CurrentUser actor,@RequestParam(required=false) String buildingId,@RequestParam(required=false) String floorId,HttpServletRequest request) {
        query(request,Set.of("buildingId","floorId")); return ApiResponse.success(service.summaries(actor,buildingId,floorId),request);
    }
    @GetMapping("/locations/{id}/items") public ApiResponse<LocationItemsVo> items(@AuthenticationPrincipal CurrentUser actor,@PathVariable String id,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize,@RequestParam(required=false) String category,HttpServletRequest request) {
        query(request,Set.of("page","pageSize","category")); return ApiResponse.success(service.items(actor,id(id),page,pageSize,category),request);
    }
}
