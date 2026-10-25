package com.company.integration_catalog.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.*;

public class AuthRateLimitFilter extends OncePerRequestFilter {
    private record Window(long startedAt, int count) {}
    private final Map<String, Window> windows = new HashMap<>();
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        if (path.isEmpty()) path = request.getRequestURI().substring(request.getContextPath().length());
        return !request.getMethod().equals("POST") || !path.startsWith("/api/v1/auth/");
    }
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean allowed;
        long now = System.nanoTime();
        synchronized (windows) {
            windows.entrySet().removeIf(entry -> now - entry.getValue().startedAt() >= 60_000_000_000L);
            Window previous = windows.get(request.getRemoteAddr());
            allowed = previous == null ? windows.size() < 10_000 : previous.count() < 20;
            if (allowed) windows.put(request.getRemoteAddr(), new Window(previous == null ? now : previous.startedAt(),
                    previous == null ? 1 : previous.count() + 1));
        }
        if (!allowed) {
            response.setStatus(429);
            response.setHeader("Retry-After", "60");
            response.setContentType("application/json");
            response.getWriter().write("{\"status\":429,\"error\":\"Too Many Requests\",\"message\":\"Try again later\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
