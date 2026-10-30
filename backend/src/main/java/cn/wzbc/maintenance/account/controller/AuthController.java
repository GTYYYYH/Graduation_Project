package cn.wzbc.maintenance.account.controller;

import cn.wzbc.maintenance.account.dto.LoginDto;
import cn.wzbc.maintenance.account.service.SessionService;
import cn.wzbc.maintenance.account.vo.CurrentUserVo;
import cn.wzbc.maintenance.common.*;
import cn.wzbc.maintenance.security.SessionAuthenticationFilter;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import java.time.Duration;
import org.springframework.core.env.Environment;
import org.springframework.http.*;
import org.springframework.security.web.csrf.*;
import org.springframework.web.bind.annotation.*;

@RestController
@org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication(type=org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type.SERVLET)
@RequestMapping("/api/auth")
public class AuthController {
    private final SessionService sessions;
    private final CookieCsrfTokenRepository csrfRepository;
    private final boolean secure;
    public AuthController(SessionService sessions,CookieCsrfTokenRepository csrfRepository,Environment environment) {
        this.sessions=sessions; this.csrfRepository=csrfRepository; this.secure=environment.getProperty("campus.cookie-secure",Boolean.class,false);
    }
    @GetMapping("/csrf") public ApiResponse<CurrentUserVo.CsrfVo> csrf(HttpServletRequest request) {
        var token=(CsrfToken)request.getAttribute(CsrfToken.class.getName());
        return ApiResponse.success(new CurrentUserVo.CsrfVo(token.getToken(),"X-CSRF-TOKEN"),request);
    }
    @PostMapping("/login") public ApiResponse<CurrentUserVo.AuthSessionVo> login(@Valid @RequestBody LoginDto dto,HttpServletRequest request,HttpServletResponse response) {
        var result=sessions.login(dto);
        response.addHeader(HttpHeaders.SET_COOKIE,cookie(result.token(),Duration.ofHours(8)));
        csrfRepository.saveToken(null,request,response);
        return ApiResponse.success(result.response(),request);
    }
    @GetMapping("/me") public ApiResponse<CurrentUserVo.AuthSessionVo> me(HttpServletRequest request) {
        var session=(SessionService.AuthenticatedSession)request.getAttribute(SessionAuthenticationFilter.SESSION_ATTRIBUTE);
        if (session==null) throw new BusinessException(HttpStatus.UNAUTHORIZED,"AUTH_REQUIRED","请先登录");
        return ApiResponse.success(new CurrentUserVo.AuthSessionVo(CurrentUserVo.from(session.user()),CurrentUserVo.instant(session.session().expiresAt)),request);
    }
    @PostMapping("/logout") public ApiResponse<Void> logout(HttpServletRequest request,HttpServletResponse response) {
        sessions.revokeCurrent(); response.addHeader(HttpHeaders.SET_COOKIE,cookie("",Duration.ZERO));
        csrfRepository.saveToken(null,request,response);
        return ApiResponse.success(null,request);
    }
    private String cookie(String value,Duration maxAge) {
        return ResponseCookie.from("CAMPUS_SESSION",value).httpOnly(true).secure(secure).sameSite("Lax").path("/api").maxAge(maxAge).build().toString();
    }
}
