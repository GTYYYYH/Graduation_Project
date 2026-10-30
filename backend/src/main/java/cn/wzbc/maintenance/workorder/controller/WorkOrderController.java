package cn.wzbc.maintenance.workorder.controller;
import cn.wzbc.maintenance.common.*;
import cn.wzbc.maintenance.workorder.vo.WorkOrderDetailVo;
import cn.wzbc.maintenance.workorder.service.WorkOrderQueryService;
import cn.wzbc.maintenance.workorder.service.WorkOrderTransitionService;
import cn.wzbc.maintenance.workorder.service.AssignmentService;
import cn.wzbc.maintenance.workorder.service.RepairProcessingService;
import cn.wzbc.maintenance.workorder.service.AcceptanceService;
import cn.wzbc.maintenance.workorder.dto.*;
import cn.wzbc.maintenance.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.Set;
import static cn.wzbc.maintenance.common.OperationResultService.*;
@RestController @RequestMapping("/api/work-orders")
public class WorkOrderController {
    private final WorkOrderQueryService service; private final WorkOrderTransitionService transitions; private final AssignmentService assignments; private final RepairProcessingService repairs; private final AcceptanceService acceptances;
    public WorkOrderController(WorkOrderQueryService service,WorkOrderTransitionService transitions,AssignmentService assignments,RepairProcessingService repairs,AcceptanceService acceptances) { this.service=service; this.transitions=transitions; this.assignments=assignments; this.repairs=repairs; this.acceptances=acceptances; }
    @GetMapping("/navigation-count") public ApiResponse<WorkOrderQueryService.NavigationCountVo> navigationCount(@AuthenticationPrincipal CurrentUser actor,HttpServletRequest request) {
        if(!request.getParameterMap().isEmpty()) throw error(400,"INVALID_QUERY","数量接口不接受查询范围覆盖");
        return ApiResponse.success(service.navigationCount(actor),request);
    }
    @GetMapping public ApiResponse<PageResult<WorkOrderDetailVo.SummaryVo>> list(@AuthenticationPrincipal CurrentUser actor,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize,@RequestParam(required=false) String keyword,@RequestParam(required=false) String status,@RequestParam(defaultValue="ALL") String scope,@RequestParam(required=false) String locationId,@RequestParam(required=false) String technicianId,HttpServletRequest request) {
        if(request.getParameterMap().entrySet().stream().anyMatch(p -> !Set.of("page","pageSize","keyword","status","scope","locationId","technicianId").contains(p.getKey()) || p.getValue().length!=1)) throw error(400,"INVALID_QUERY","查询字段不合法");
        return ApiResponse.success(service.list(actor,page,pageSize,keyword,status,scope,locationId,technicianId,null),request);
    }
    @GetMapping("/{id}") public ApiResponse<WorkOrderDetailVo> detail(@AuthenticationPrincipal CurrentUser actor,@PathVariable String id,HttpServletRequest request) { return ApiResponse.success(service.detail(actor,id(id)),request); }
    @PostMapping("/{id}/accept") public ApiResponse<OperationResultService.Receipt> accept(@AuthenticationPrincipal CurrentUser actor,@PathVariable String id,@RequestBody AcceptWorkOrderDto dto,HttpServletRequest request) { return ApiResponse.success(transitions.accept(actor,id(id),dto),request); }
    @PostMapping("/{id}/refuse") public ApiResponse<OperationResultService.Receipt> refuse(@AuthenticationPrincipal CurrentUser actor,@PathVariable String id,@RequestBody RefuseWorkOrderDto dto,HttpServletRequest request) { return ApiResponse.success(transitions.refuse(actor,id(id),dto),request); }
    @PostMapping("/{id}/assign") public ApiResponse<OperationResultService.Receipt> assign(@AuthenticationPrincipal CurrentUser actor,@PathVariable String id,@RequestBody AssignWorkOrderDto dto,HttpServletRequest request) { return ApiResponse.success(assignments.reassign(actor,id(id),dto),request); }
    @PostMapping("/{id}/progress") public ApiResponse<OperationResultService.Receipt> progress(@AuthenticationPrincipal CurrentUser actor,@PathVariable String id,@RequestBody ProgressDto dto,HttpServletRequest request) { return ApiResponse.success(repairs.progress(actor,id(id),dto),request); }
    @PostMapping("/{id}/submit-result") public ApiResponse<OperationResultService.Receipt> result(@AuthenticationPrincipal CurrentUser actor,@PathVariable String id,@RequestBody SubmitRepairResultDto dto,HttpServletRequest request) { return ApiResponse.success(repairs.submit(actor,id(id),dto),request); }
    @PostMapping("/{id}/acceptance") public ApiResponse<OperationResultService.Receipt> acceptance(@AuthenticationPrincipal CurrentUser actor,@PathVariable String id,@RequestBody AcceptanceDto dto,HttpServletRequest request) { return ApiResponse.success(acceptances.decide(actor,id(id),dto),request); }
}
