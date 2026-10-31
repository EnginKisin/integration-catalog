package com.company.integration_catalog.security;

import com.company.integration_catalog.exception.ApiErrors;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.authorization.AuthorizationDecision;
import com.company.integration_catalog.model.enums.UserPermission;
import com.company.integration_catalog.repository.UserAccountRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

@Configuration
public class SecurityConfig {
    @Bean
    public PasswordEncoder passwordEncoder() {
        return Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper mapper, CredentialAccess credentialAccess,
            UserAccountRepository accounts) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/liveness",
                                "/actuator/health/readiness").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/csrf").permitAll()
                        .requestMatchers("/api/v1/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/invitations/accept",
                                "/api/v1/auth/password-reset/request", "/api/v1/auth/password-reset/confirm",
                                "/api/v1/auth/email/confirm").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/password/change", "/api/v1/auth/email/change").authenticated()
                        .requestMatchers("/api/v1/auth/me").authenticated()
                        .requestMatchers("/api/v1/users", "/api/v1/users/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/integrations/*/mappings/context",
                                "/api/v1/integrations/*/mappings/ai/capabilities",
                                "/api/v1/integrations/*/mappings/ai/jobs/*",
                                "/api/v1/integrations/*/mappings/ai/jobs/*/result")
                                .hasAnyRole("ADMIN", "EDITOR", "VIEWER")
                        .requestMatchers(HttpMethod.POST, "/api/v1/integrations/*/mappings/ai/jobs",
                                "/api/v1/integrations/*/mappings/fields/append")
                                .hasAnyRole("ADMIN", "EDITOR")
                        .requestMatchers(HttpMethod.GET, "/api/v1/integrations/*/configs/*/*/credentials")
                                .access((authentication, context) -> new AuthorizationDecision(
                                        credentialAccess.allowed(authentication.get(), UserPermission.VIEW_CREDENTIALS)))
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/integrations", "/api/v1/integrations/*",
                                "/api/v1/integrations/*/mappings/schemas", "/api/v1/integrations/*/mappings/fields",
                                "/api/v1/integrations/*/mappings/schemas/*/sample",
                                "/api/v1/systems")
                                .hasAnyRole("ADMIN", "EDITOR", "VIEWER")
                        .requestMatchers(HttpMethod.GET, "/api/v1/systems/*")
                                .hasAnyRole("ADMIN", "EDITOR", "VIEWER")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/systems/*")
                                .hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/systems/*")
                                .hasAnyRole("ADMIN", "EDITOR")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/systems/*/active")
                                .hasAnyRole("ADMIN", "EDITOR")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/integrations/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/integrations", "/api/v1/integrations/*/configs",
                                "/api/v1/integrations/*/review/confirm",
                                "/api/v1/integrations/*/mappings/schemas/upload",
                                "/api/v1/integrations/*/mappings/schemas", "/api/v1/integrations/*/mappings/fields",
                                "/api/v1/systems")
                                .hasAnyRole("ADMIN", "EDITOR")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/integrations/*").hasAnyRole("ADMIN", "EDITOR")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/integrations/*/complete").hasAnyRole("ADMIN", "EDITOR")
                        .anyRequest().denyAll())
                .addFilterAfter(new SessionVersionFilter(accounts, mapper), SecurityContextHolderFilter.class)
                .addFilterBefore(new AuthRateLimitFilter(mapper), UsernamePasswordAuthenticationFilter.class)
                .requestCache(cache -> cache.disable())
                .formLogin(form -> form.loginProcessingUrl("/api/v1/auth/login")
                        .successHandler((request, response, authentication) -> response.setStatus(200))
                        .failureHandler((request, response, exception) ->
                                error(mapper, request, response, 401, "INVALID_CREDENTIALS")))
                .logout(logout -> logout.logoutUrl("/api/v1/auth/logout")
                        .invalidateHttpSession(true).deleteCookies("JSESSIONID")
                        .logoutSuccessHandler((request, response, authentication) -> response.setStatus(204)))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                error(mapper, request, response, 401, Boolean.TRUE.equals(request.getAttribute(SessionVersionFilter.INVALID_SESSION)) ? "SESSION_INVALID" : "AUTH_REQUIRED"))
                        .accessDeniedHandler((request, response, exception) ->
                                error(mapper, request, response, 403, deniedCode(request, exception))));
        return http.build();
    }

    private String deniedCode(HttpServletRequest request, org.springframework.security.access.AccessDeniedException exception) {
        if (exception instanceof org.springframework.security.web.csrf.MissingCsrfTokenException) return "CSRF_MISSING";
        if (exception instanceof org.springframework.security.web.csrf.CsrfException) return "CSRF_INVALID";
        String path = request.getServletPath();
        if (path.isEmpty()) path = request.getRequestURI().substring(request.getContextPath().length());
        if (request.getMethod().equals("GET") && path.matches("/api/v1/integrations/[^/]+/configs/[^/]+/[^/]+/credentials"))
            return "CREDENTIAL_PERMISSION_REQUIRED";
        return "ACCESS_DENIED";
    }

    private void error(ObjectMapper mapper, HttpServletRequest request, HttpServletResponse response,
                       int status, String code) throws IOException {
        ApiErrors.write(mapper, request, response, status, code, null);
    }
}
