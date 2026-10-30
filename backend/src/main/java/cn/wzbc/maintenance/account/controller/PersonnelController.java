package cn.wzbc.maintenance.account.controller;
import cn.wzbc.maintenance.account.dto.*;
import cn.wzbc.maintenance.account.service.*;
import cn.wzbc.maintenance.common.*;
import cn.wzbc.maintenance.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController
public class PersonnelController {
    private final PersonnelQueryService query;private final AccountAdministrationService administration;
    public PersonnelController(PersonnelQueryService query,AccountAdministrationService administration){this.query=query;this.administration=administration;}
    @GetMapping("/api/admin/personnel")public ApiResponse<?> list(@AuthenticationPrincipal CurrentUser actor,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int pageSize,@RequestParam(defaultValue="ALL")String category,@RequestParam(required=false)String role,@RequestParam(required=false)String reviewStatus,@RequestParam(required=false)Boolean enabled,@RequestParam(required=false)String keyword,HttpServletRequest request){return ApiResponse.success(query.list(actor,page,pageSize,keyword,category,role,reviewStatus,enabled),request);}
    @GetMapping("/api/admin/personnel/{id}/history")public ApiResponse<?> history(@AuthenticationPrincipal CurrentUser actor,@PathVariable String id,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int pageSize,@RequestParam(required=false)String category,@RequestParam(required=false)String requestStatus,@RequestParam(required=false)String workOrderStatus,@RequestParam(required=false)String keyword,HttpServletRequest request){return ApiResponse.success(query.history(actor,OperationResultService.id(id),category,"REQUESTS".equals(category)?requestStatus:workOrderStatus,keyword,page,pageSize),request);}
    @PostMapping("/api/admin/accounts/{id}/enable")public ApiResponse<?> enable(@AuthenticationPrincipal CurrentUser actor,@PathVariable String id,@Valid @RequestBody AccountStatusDto dto,HttpServletRequest request){return ApiResponse.success(administration.status(actor,OperationResultService.id(id),dto,true),request);}
    @PostMapping("/api/admin/accounts/{id}/disable")public ApiResponse<?> disable(@AuthenticationPrincipal CurrentUser actor,@PathVariable String id,@Valid @RequestBody AccountStatusDto dto,HttpServletRequest request){return ApiResponse.success(administration.status(actor,OperationResultService.id(id),dto,false),request);}
    @PostMapping("/api/admin/accounts/{id}/reset-password")public ApiResponse<?> reset(@AuthenticationPrincipal CurrentUser actor,@PathVariable String id,@Valid @RequestBody ResetPasswordDto dto,HttpServletRequest request){return ApiResponse.success(administration.reset(actor,OperationResultService.id(id),dto),request);}
}
