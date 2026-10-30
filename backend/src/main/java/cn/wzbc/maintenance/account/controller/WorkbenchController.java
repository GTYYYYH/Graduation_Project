package cn.wzbc.maintenance.account.controller;
import cn.wzbc.maintenance.account.service.WorkbenchQueryService;
import cn.wzbc.maintenance.common.ApiResponse;
import cn.wzbc.maintenance.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController
public class WorkbenchController {
    private final WorkbenchQueryService service;
    public WorkbenchController(WorkbenchQueryService service){this.service=service;}
    @GetMapping("/api/workbench")public ApiResponse<WorkbenchQueryService.WorkbenchVo> query(@AuthenticationPrincipal CurrentUser actor,HttpServletRequest request){if(!request.getParameterMap().isEmpty())throw cn.wzbc.maintenance.common.OperationResultService.error(400,"INVALID_QUERY","工作台不接受角色或范围覆盖");return ApiResponse.success(service.query(actor),request);}
}
