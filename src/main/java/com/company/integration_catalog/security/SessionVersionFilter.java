package com.company.integration_catalog.security;

import com.company.integration_catalog.repository.UserAccountRepository;
import com.company.integration_catalog.exception.ApiErrors;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

@RequiredArgsConstructor
public class SessionVersionFilter extends OncePerRequestFilter {
    public static final String INVALID_SESSION = SessionVersionFilter.class.getName() + ".invalidSession";
    private final UserAccountRepository accounts;
    private final ObjectMapper mapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        if (path.isEmpty()) path = request.getRequestURI().substring(request.getContextPath().length());
        return path.startsWith("/actuator/health");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AccountPrincipal principal) {
            boolean valid;
            try {
                valid = accounts.findById(principal.getAccountId()).map(account -> account.loginAllowed()
                        && account.getSessionVersion() == principal.getSessionVersion()).orElse(false);
            } catch (RuntimeException ex) {
                ApiErrors.write(mapper, request, response, 503, "ACCOUNT_VERIFICATION_UNAVAILABLE", null);
                return;
            }
            if (!valid) {
                request.setAttribute(INVALID_SESSION, true);
                if (request.getSession(false) != null) request.getSession(false).invalidate();
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}
