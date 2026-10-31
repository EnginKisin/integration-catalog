package com.company.integration_catalog.security;

import com.company.integration_catalog.exception.ApiErrors;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.*;

@RequiredArgsConstructor
public class AuthRateLimitFilter extends OncePerRequestFilter {
    private record Window(long startedAt, int count) {}
    private final ObjectMapper mapper;
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
        long retryAfter = 60;
        long now = System.nanoTime();
        synchronized (windows) {
            windows.entrySet().removeIf(entry -> now - entry.getValue().startedAt() >= 60_000_000_000L);
            Window previous = windows.get(request.getRemoteAddr());
            allowed = previous == null ? windows.size() < 10_000 : previous.count() < 20;
            if (allowed) {
                windows.put(request.getRemoteAddr(), new Window(previous == null ? now : previous.startedAt(),
                        previous == null ? 1 : previous.count() + 1));
            } else {
                long startedAt = previous == null ? windows.values().stream().mapToLong(Window::startedAt).min().orElse(now)
                        : previous.startedAt();
                retryAfter = Math.max(1, (60_000_000_000L - (now - startedAt) + 999_999_999L) / 1_000_000_000L);
            }
        }
        if (!allowed) {
            ApiErrors.write(mapper, request, response, 429, "RATE_LIMITED", retryAfter);
            return;
        }
        chain.doFilter(request, response);
    }
}
