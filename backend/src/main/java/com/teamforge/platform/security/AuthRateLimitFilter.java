package com.teamforge.platform.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamforge.platform.api.ApiErrors;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.Clock;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.springframework.web.filter.OncePerRequestFilter;

/** Single-instance, bounded IP limiter; never trusts unconfigured forwarding headers. */
public final class AuthRateLimitFilter extends OncePerRequestFilter {
    private record Window(long start, int count) {}
    private final Map<String, Window> attempts = new HashMap<>();
    private final ObjectMapper mapper;
    private final Clock clock;
    private final int limit;
    public AuthRateLimitFilter(ObjectMapper mapper, Clock clock, int limit) {
        this.mapper = mapper; this.clock = clock; this.limit = limit;
    }
    private synchronized boolean allow(String key) {
        long now = clock.millis();
        attempts.entrySet().removeIf(entry -> now - entry.getValue().start() >= 60_000);
        Window window = attempts.get(key);
        if (window == null && attempts.size() >= 10_000) return false;
        if (window != null && window.count() >= limit) return false;
        attempts.put(key, new Window(window == null ? now : window.start(), window == null ? 1 : window.count() + 1));
        return true;
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                              FilterChain chain) throws ServletException, IOException {
        String path = request.getServletPath();
        if ("POST".equals(request.getMethod())
            && (path.equals("/api/v1/auth/login") || path.equals("/api/v1/auth/register"))
            && !allow(request.getRemoteAddr())) {
            response.setHeader("Retry-After", "60");
            ApiErrors.write(response, mapper, 429, "RATE_LIMITED", "Too many attempts. Try again shortly.");
            return;
        }
        if ("POST".equals(request.getMethod()) && path.equals("/api/v1/auth/login")) {
            String email = request.getParameter("email");
            String password = request.getParameter("password");
            if (email == null || email.length() > 254 || password == null || password.isEmpty()
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
                ApiErrors.write(response, mapper, 401, "INVALID_CREDENTIALS", "Email or password is incorrect.");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
