package com.company.integration_catalog.security;

import com.company.integration_catalog.exception.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
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
import java.time.LocalDateTime;

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
                        .requestMatchers(HttpMethod.GET, "/api/v1/integrations/*/configs/*/*/credentials")
                                .access((authentication, context) -> new AuthorizationDecision(
                                        credentialAccess.allowed(authentication.get(), UserPermission.VIEW_CREDENTIALS)))
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/integrations", "/api/v1/integrations/*",
                                "/api/v1/integrations/*/mappings/schemas", "/api/v1/integrations/*/mappings/fields",
                                "/api/v1/integrations/*/mappings/schemas/*/sample",
                                "/api/v1/sources", "/api/v1/destinations", "/api/v1/middlewares")
                                .hasAnyRole("ADMIN", "EDITOR", "VIEWER")
                        .requestMatchers(HttpMethod.GET, "/api/v1/sources/*", "/api/v1/destinations/*", "/api/v1/middlewares/*")
                                .hasAnyRole("ADMIN", "EDITOR", "VIEWER")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/sources/*", "/api/v1/destinations/*", "/api/v1/middlewares/*")
                                .hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/sources/*", "/api/v1/destinations/*", "/api/v1/middlewares/*")
                                .hasAnyRole("ADMIN", "EDITOR")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/sources/*/active", "/api/v1/destinations/*/active", "/api/v1/middlewares/*/active")
                                .hasAnyRole("ADMIN", "EDITOR")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/integrations/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/integrations", "/api/v1/integrations/*/configs",
                                "/api/v1/integrations/*/review/confirm",
                                "/api/v1/integrations/*/mappings/schemas/upload",
                                "/api/v1/integrations/*/mappings/schemas", "/api/v1/integrations/*/mappings/fields",
                                "/api/v1/sources", "/api/v1/destinations", "/api/v1/middlewares")
                                .hasAnyRole("ADMIN", "EDITOR")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/integrations/*").hasAnyRole("ADMIN", "EDITOR")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/integrations/*/complete").hasAnyRole("ADMIN", "EDITOR")
                        .anyRequest().denyAll())
                .addFilterAfter(new SessionVersionFilter(accounts, mapper), SecurityContextHolderFilter.class)
                .addFilterBefore(new AuthRateLimitFilter(), UsernamePasswordAuthenticationFilter.class)
                .requestCache(cache -> cache.disable())
                .formLogin(form -> form.loginProcessingUrl("/api/v1/auth/login")
                        .successHandler((request, response, authentication) -> response.setStatus(200))
                        .failureHandler((request, response, exception) ->
                                error(mapper, request, response, 401, "Unauthorized", "Invalid username or password")))
                .logout(logout -> logout.logoutUrl("/api/v1/auth/logout")
                        .invalidateHttpSession(true).deleteCookies("JSESSIONID")
                        .logoutSuccessHandler((request, response, authentication) -> response.setStatus(204)))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                error(mapper, request, response, 401, "Unauthorized", "Authentication required"))
                        .accessDeniedHandler((request, response, exception) ->
                                error(mapper, request, response, 403, "Forbidden", "Access denied")));
        return http.build();
    }

    private void error(ObjectMapper mapper, HttpServletRequest request, HttpServletResponse response,
                       int status, String error, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(), ErrorResponse.builder()
                .timestamp(LocalDateTime.now()).status(status).error(error).message(message)
                .path(request.getRequestURI()).build());
    }
}
