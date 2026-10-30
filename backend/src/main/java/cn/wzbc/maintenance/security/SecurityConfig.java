package cn.wzbc.maintenance.security;

import cn.wzbc.maintenance.common.ApiResponse;
import cn.wzbc.maintenance.account.service.SessionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration(proxyBeanMethods = false)
@org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication(type = org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfig {
    @Bean CookieCsrfTokenRepository csrfRepository(org.springframework.core.env.Environment environment) {
        var csrf = new CookieCsrfTokenRepository();
        csrf.setHeaderName("X-CSRF-TOKEN");
        csrf.setCookieCustomizer(cookie -> cookie.httpOnly(true).sameSite("Lax").path("/api")
                .secure(environment.getProperty("campus.cookie-secure",Boolean.class,false)));
        return csrf;
    }
    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityErrorHandler errors, CookieCsrfTokenRepository csrf, SessionService sessions) throws Exception {
        return http.formLogin(AbstractHttpConfigurer::disable).httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable).requestCache(AbstractHttpConfigurer::disable)
                // Cookie身份每请求重建；禁止HttpSession自动认证策略把每次me误当成新登录并清CSRF。
                .sessionManagement(AbstractHttpConfigurer::disable)
                .securityContext(config -> config.securityContextRepository(new org.springframework.security.web.context.RequestAttributeSecurityContextRepository()))
                .csrf(config -> config.csrfTokenRepository(csrf))
                .addFilterBefore(new SessionAuthenticationFilter(sessions),org.springframework.security.web.csrf.CsrfFilter.class)
                .exceptionHandling(config -> config.authenticationEntryPoint(errors).accessDeniedHandler(errors))
                .authorizeHttpRequests(auth -> auth.requestMatchers(org.springframework.http.HttpMethod.GET,"/api/auth/csrf").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.POST,"/api/auth/register","/api/auth/login","/api/auth/logout").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET,"/api/account/review").hasRole("TECHNICIAN")
                        .requestMatchers(org.springframework.http.HttpMethod.GET,"/api/account/profile").authenticated()
                        .requestMatchers(org.springframework.http.HttpMethod.GET,"/api/workbench").authenticated()
                        .requestMatchers(org.springframework.http.HttpMethod.GET,"/api/statistics/dashboard","/api/statistics/details").hasRole("ADMIN")
                        .requestMatchers(org.springframework.http.HttpMethod.PATCH,"/api/account/profile").authenticated()
                        .requestMatchers(org.springframework.http.HttpMethod.POST,"/api/account/password").authenticated()
                        .requestMatchers(org.springframework.http.HttpMethod.GET,"/api/admin/personnel","/api/admin/personnel/*/history").hasRole("ADMIN")
                        .requestMatchers(org.springframework.http.HttpMethod.POST,"/api/admin/accounts/*/enable","/api/admin/accounts/*/disable","/api/admin/accounts/*/reset-password").hasRole("ADMIN")
                        .requestMatchers(org.springframework.http.HttpMethod.POST,"/api/account/review/resubmit").hasRole("TECHNICIAN")
                        .requestMatchers(org.springframework.http.HttpMethod.GET,"/api/admin/accounts/*/registration-review","/api/admin/registration-reviews","/api/admin/repair-requests").hasRole("ADMIN")
                        .requestMatchers(org.springframework.http.HttpMethod.POST,"/api/admin/accounts/*/registration-review").hasRole("ADMIN")
                        .requestMatchers(org.springframework.http.HttpMethod.POST,"/api/attachments/temp").hasAnyRole("REPORTER","TECHNICIAN","ADMIN")
                        .requestMatchers(org.springframework.http.HttpMethod.GET,"/api/attachments/*/content").authenticated()
                        .requestMatchers(org.springframework.http.HttpMethod.DELETE,"/api/attachments/*/temp").authenticated()
                        .requestMatchers(org.springframework.http.HttpMethod.POST,"/api/repair-requests").hasRole("REPORTER")
                        .requestMatchers(org.springframework.http.HttpMethod.GET,"/api/repair-requests").hasAnyRole("REPORTER","ADMIN")
                        .requestMatchers(org.springframework.http.HttpMethod.GET,"/api/repair-requests/*","/api/operations/*").authenticated()
                        .requestMatchers(org.springframework.http.HttpMethod.GET,"/api/work-orders","/api/work-orders/*").authenticated()
                        .requestMatchers(org.springframework.http.HttpMethod.POST,"/api/work-orders/*/accept","/api/work-orders/*/refuse","/api/work-orders/*/progress","/api/work-orders/*/submit-result").hasRole("TECHNICIAN")
                        .requestMatchers(org.springframework.http.HttpMethod.POST,"/api/work-orders/*/assign").hasRole("ADMIN")
                        .requestMatchers(org.springframework.http.HttpMethod.POST,"/api/work-orders/*/acceptance").hasRole("REPORTER")
                        .requestMatchers(org.springframework.http.HttpMethod.GET,"/api/equipment","/api/equipment/*","/api/equipment/*/work-orders").hasRole("ADMIN")
                        .requestMatchers(org.springframework.http.HttpMethod.POST,"/api/equipment","/api/equipment/*/enable","/api/equipment/*/disable").hasRole("ADMIN")
                        .requestMatchers(org.springframework.http.HttpMethod.PATCH,"/api/equipment/*").hasRole("ADMIN")
                        .requestMatchers(org.springframework.http.HttpMethod.POST,"/api/repair-requests/*/return").hasRole("ADMIN")
                        .requestMatchers(org.springframework.http.HttpMethod.POST,"/api/repair-requests/*/approve-and-assign","/api/repair-requests/*/duplicate-close").hasRole("ADMIN")
                        .requestMatchers(org.springframework.http.HttpMethod.GET,"/api/admin/assignment-eligible").hasRole("ADMIN")
                        .requestMatchers(org.springframework.http.HttpMethod.POST,"/api/repair-requests/*/supplement","/api/repair-requests/*/withdraw").hasRole("REPORTER")
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/auth/me","/api/campus/catalog","/api/campus/summaries","/api/campus/locations/*/items")
                        .authenticated().anyRequest().denyAll()).build();
    }

    @Bean FilterRegistrationBean<OncePerRequestFilter> requestMetadataFilter() {
        var filter = new OncePerRequestFilter() {
            @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                    throws ServletException, IOException {
                response.setHeader("X-Request-Id", ApiResponse.requestId(request));
                response.setHeader("Cache-Control", "private, no-store");
                chain.doFilter(request, response);
            }
        };
        var registration = new FilterRegistrationBean<OncePerRequestFilter>(filter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
