package com.xuelvquan.travel.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 拦截/api/**：token合法则写入上下文；缺失/非法时放行（公开只读接口可匿名访问，
 * 写操作由控制器内AuthUtil.requireUserId()兜底抛401）。
 */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // CORS预检直接放行
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            String token = auth.substring(7);
            Claims claims = jwtUtil.parse(token);
            if (claims != null) {
                Long userId = Long.valueOf(claims.getSubject());
                String role = claims.get("role", String.class);
                String username = claims.get("username", String.class);
                AuthContext.set(userId, role, username);
                request.setAttribute("userId", userId);
                request.setAttribute("role", role);
            }
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        AuthContext.clear();
    }
}
