package cn.wzbc.maintenance.repairrequest.controller;
import cn.wzbc.maintenance.repairrequest.dto.*;
import cn.wzbc.maintenance.repairrequest.service.RepairRequestService;
import cn.wzbc.maintenance.repairrequest.vo.RepairRequestDetailVo;
import cn.wzbc.maintenance.common.*;
import cn.wzbc.maintenance.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController
public class RepairRequestController {
    private final RepairRequestService service;
    public RepairRequestController(RepairRequestService service) { this.service=service; }
    @PostMapping("/api/repair-requests") public ResponseEntity<ApiResponse<OperationResultService.Receipt>> create(@AuthenticationPrincipal CurrentUser actor,@Valid @RequestBody CreateRepairRequestDto dto,HttpServletRequest request) { return ResponseEntity.status(201).body(ApiResponse.success(service.create(actor,dto),request)); }
    @GetMapping("/api/repair-requests") public ApiResponse<PageResult<RepairRequestDetailVo.SummaryVo>> list(@AuthenticationPrincipal CurrentUser actor,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize,@RequestParam(required=false) String keyword,@RequestParam(required=false) String status,@RequestParam(required=false) String locationId,HttpServletRequest request) { return ApiResponse.success(service.list(actor,page,pageSize,keyword,status,locationId),request); }
    @GetMapping("/api/repair-requests/{id}") public ApiResponse<RepairRequestDetailVo> detail(@AuthenticationPrincipal CurrentUser actor,@PathVariable String id,HttpServletRequest request) { return ApiResponse.success(service.detail(actor,OperationResultService.id(id)),request); }
    @PostMapping("/api/repair-requests/{id}/return") public ApiResponse<OperationResultService.Receipt> returned(@AuthenticationPrincipal CurrentUser actor,@PathVariable String id,@Valid @RequestBody ReturnRequestDto dto,HttpServletRequest request) { return ApiResponse.success(service.returnRequest(actor,OperationResultService.id(id),dto),request); }
    @PostMapping("/api/repair-requests/{id}/supplement") public ApiResponse<OperationResultService.Receipt> supplement(@AuthenticationPrincipal CurrentUser actor,@PathVariable String id,@Valid @RequestBody SupplementRequestDto dto,HttpServletRequest request) { return ApiResponse.success(service.supplement(actor,OperationResultService.id(id),dto),request); }
    @PostMapping("/api/repair-requests/{id}/withdraw") public ApiResponse<OperationResultService.Receipt> withdraw(@AuthenticationPrincipal CurrentUser actor,@PathVariable String id,@Valid @RequestBody WithdrawRequestDto dto,HttpServletRequest request) { return ApiResponse.success(service.withdraw(actor,OperationResultService.id(id),dto),request); }
}
