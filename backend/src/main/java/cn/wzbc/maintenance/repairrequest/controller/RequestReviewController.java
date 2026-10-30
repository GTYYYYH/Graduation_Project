package cn.wzbc.maintenance.repairrequest.controller;
import cn.wzbc.maintenance.repairrequest.dto.*;
import cn.wzbc.maintenance.repairrequest.service.RequestReviewService;
import cn.wzbc.maintenance.repairrequest.service.RepairRequestService;
import cn.wzbc.maintenance.repairrequest.vo.RepairRequestDetailVo;
import cn.wzbc.maintenance.workorder.service.AssignmentService;
import cn.wzbc.maintenance.common.*;
import cn.wzbc.maintenance.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController
public class RequestReviewController {
    private final RequestReviewService reviews; private final AssignmentService assignments; private final RepairRequestService requests;
    public RequestReviewController(RequestReviewService reviews,AssignmentService assignments,RepairRequestService requests) { this.reviews=reviews; this.assignments=assignments; this.requests=requests; }
    @GetMapping("/api/admin/repair-requests") public ApiResponse<PageResult<RepairRequestDetailVo.SummaryVo>> list(@AuthenticationPrincipal CurrentUser actor,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize,@RequestParam(required=false) String keyword,@RequestParam(required=false) String status,@RequestParam(required=false) String locationId,HttpServletRequest request) {
        if(actor==null || actor.role()!=CurrentUser.Role.ADMIN) throw OperationResultService.error(403,"FORBIDDEN","仅管理员可以读取审核列表");
        return ApiResponse.success(requests.list(actor,page,pageSize,keyword,status,locationId),request);
    }
    @PostMapping("/api/repair-requests/{id}/approve-and-assign") public ApiResponse<OperationResultService.Receipt> approve(@AuthenticationPrincipal CurrentUser actor,@PathVariable String id,@Valid @RequestBody ApproveAndAssignDto dto,HttpServletRequest request) { return ApiResponse.success(reviews.approveAndAssign(actor,OperationResultService.id(id),dto),request); }
    @PostMapping("/api/repair-requests/{id}/duplicate-close") public ApiResponse<OperationResultService.Receipt> close(@AuthenticationPrincipal CurrentUser actor,@PathVariable String id,@Valid @RequestBody DuplicateCloseDto dto,HttpServletRequest request) { return ApiResponse.success(reviews.duplicateClose(actor,OperationResultService.id(id),dto),request); }
    @GetMapping("/api/admin/assignment-eligible") public ApiResponse<AssignmentService.AssignmentEligibleVo> eligible(@AuthenticationPrincipal CurrentUser actor,@RequestParam String context,@RequestParam(required=false) String requestId,@RequestParam(required=false) String workOrderId,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize,@RequestParam(required=false) String keyword,HttpServletRequest request) { return ApiResponse.success(assignments.eligible(actor,context,requestId,workOrderId,page,pageSize,keyword),request); }
}
