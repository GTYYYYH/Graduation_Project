package cn.wzbc.maintenance.account.controller;

import cn.wzbc.maintenance.account.dto.*;
import cn.wzbc.maintenance.account.service.TechnicianReviewService;
import cn.wzbc.maintenance.account.vo.TechnicianReviewVo;
import cn.wzbc.maintenance.common.*;
import cn.wzbc.maintenance.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class TechnicianReviewController {
    private final TechnicianReviewService service;
    public TechnicianReviewController(TechnicianReviewService service) { this.service=service; }
    @GetMapping("/api/account/review") public ApiResponse<TechnicianReviewVo> own(@AuthenticationPrincipal CurrentUser user,HttpServletRequest request) { return ApiResponse.success(service.own(user),request); }
    @PostMapping("/api/account/review/resubmit") public ApiResponse<TechnicianReviewVo.MutationReceiptVo> resubmit(@AuthenticationPrincipal CurrentUser user,@Valid @RequestBody ResubmitReviewDto dto,HttpServletRequest request) { return ApiResponse.success(service.resubmit(user,dto),request); }
    @GetMapping("/api/admin/accounts/{id}/registration-review") public ApiResponse<TechnicianReviewVo> detail(@AuthenticationPrincipal CurrentUser user,@PathVariable String id,HttpServletRequest request) { return ApiResponse.success(service.administrative(user,id(id)),request); }
    @PostMapping("/api/admin/accounts/{id}/registration-review") public ApiResponse<TechnicianReviewVo.MutationReceiptVo> decide(@AuthenticationPrincipal CurrentUser user,@PathVariable String id,@Valid @RequestBody ReviewDecisionDto dto,HttpServletRequest request) { return ApiResponse.success(service.decide(user,id(id),dto),request); }
    @GetMapping("/api/admin/registration-reviews") public ApiResponse<PageResult<TechnicianReviewVo.PendingReviewVo>> pending(@AuthenticationPrincipal CurrentUser user,
            @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize,@RequestParam(required=false) String keyword,HttpServletRequest request) {
        return ApiResponse.success(service.pending(user,page,pageSize,keyword),request);
    }
    private static long id(String value) {
        try { if (!value.matches("[1-9][0-9]{0,18}")) throw new NumberFormatException(); return Long.parseLong(value); }
        catch (NumberFormatException error) { throw new BusinessException(HttpStatus.BAD_REQUEST,"VALIDATION_FAILED","账号ID不合法"); }
    }
}
