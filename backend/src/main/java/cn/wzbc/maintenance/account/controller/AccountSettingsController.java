package cn.wzbc.maintenance.account.controller;
import cn.wzbc.maintenance.account.dto.*;
import cn.wzbc.maintenance.account.service.AccountSettingsService;
import cn.wzbc.maintenance.common.*;
import cn.wzbc.maintenance.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController
@org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication(type=org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type.SERVLET)
public class AccountSettingsController {
    private final AccountSettingsService service;private final org.springframework.security.web.csrf.CookieCsrfTokenRepository csrf;private final boolean secure;
    public AccountSettingsController(AccountSettingsService service,org.springframework.security.web.csrf.CookieCsrfTokenRepository csrf,org.springframework.core.env.Environment environment){this.service=service;this.csrf=csrf;this.secure=environment.getProperty("campus.cookie-secure",Boolean.class,false);}
    @GetMapping("/api/account/profile")public ApiResponse<AccountSettingsService.ProfileVo> profile(@AuthenticationPrincipal CurrentUser actor,HttpServletRequest request){return ApiResponse.success(service.profile(actor),request);}
    @PatchMapping("/api/account/profile")public ApiResponse<OperationResultService.Receipt> update(@AuthenticationPrincipal CurrentUser actor,@Valid @RequestBody UpdateProfileDto dto,HttpServletRequest request){return ApiResponse.success(service.update(actor,dto),request);}
    @PostMapping("/api/account/password")public ApiResponse<OperationResultService.Receipt> password(@AuthenticationPrincipal CurrentUser actor,@Valid @RequestBody ChangePasswordDto dto,HttpServletRequest request,jakarta.servlet.http.HttpServletResponse response){var result=service.password(actor,dto);if(result.changed()){response.addHeader(org.springframework.http.HttpHeaders.SET_COOKIE,org.springframework.http.ResponseCookie.from("CAMPUS_SESSION","").httpOnly(true).secure(secure).sameSite("Lax").path("/api").maxAge(java.time.Duration.ZERO).build().toString());csrf.saveToken(null,request,response);}return ApiResponse.success(result.receipt(),request);}
}
