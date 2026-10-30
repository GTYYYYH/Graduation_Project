package cn.wzbc.maintenance.security;

import cn.wzbc.maintenance.account.service.SessionService;
import cn.wzbc.maintenance.common.BusinessException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class SessionAuthenticationFilter extends OncePerRequestFilter {
    public static final String SESSION_ATTRIBUTE="campus.authenticatedSession";
    public static final String ERROR_ATTRIBUTE="campus.sessionError";
    private final SessionService sessions;
    public SessionAuthenticationFilter(SessionService sessions) { this.sessions=sessions; }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws IOException,ServletException {
        String token=null;
        if (request.getCookies()!=null) for (var cookie:request.getCookies()) if (cookie.getName().equals("CAMPUS_SESSION")) { token=cookie.getValue(); break; }
        if (token!=null) {
            try {
                var found=sessions.authenticate(token);
                var auth=UsernamePasswordAuthenticationToken.authenticated(found.user().identity(),null,List.of(new SimpleGrantedAuthority("ROLE_"+found.user().role)));
                auth.setDetails(found.session().id);
                var context=SecurityContextHolder.createEmptyContext(); context.setAuthentication(auth); SecurityContextHolder.setContext(context);
                request.setAttribute(SESSION_ATTRIBUTE,found);
            } catch (BusinessException error) {
                SecurityContextHolder.clearContext(); request.setAttribute(ERROR_ATTRIBUTE,error);
            }
        }
        chain.doFilter(request,response);
    }
}
