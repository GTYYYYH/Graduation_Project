package cn.wzbc.maintenance.security;

import cn.wzbc.maintenance.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class SecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {
    private final ObjectMapper mapper;
    public SecurityErrorHandler(ObjectMapper mapper) { this.mapper = mapper; }

    @Override public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException error) throws IOException {
        if (request.getAttribute(SessionAuthenticationFilter.ERROR_ATTRIBUTE) instanceof cn.wzbc.maintenance.common.BusinessException failure) {
            write(request,response,401,failure.code(),failure.getMessage()); return;
        }
        write(request, response, 401, "AUTH_REQUIRED", "请先登录");
    }
    @Override public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException error) throws IOException {
        boolean csrf = error instanceof CsrfException;
        write(request, response, 403, csrf ? "CSRF_INVALID" : "FORBIDDEN", csrf ? "请求校验失败，请重新获取校验信息" : "没有操作权限");
    }
    private void write(HttpServletRequest request, HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.setHeader("Cache-Control", "private, no-store");
        response.setHeader("X-Request-Id", ApiResponse.requestId(request));
        mapper.writeValue(response.getWriter(), ApiResponse.error(code, message, request));
    }
}
