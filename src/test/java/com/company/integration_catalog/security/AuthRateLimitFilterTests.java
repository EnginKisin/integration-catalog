package com.company.integration_catalog.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import jakarta.servlet.FilterChain;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthRateLimitFilterTests {
    @Test void decodedServletPathCannotBypassRateLimit() throws Exception {
        var filter = new AuthRateLimitFilter(new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules());
        FilterChain chain = mock(FilterChain.class);
        for (int i = 0; i < 21; i++) {
            var request = new MockHttpServletRequest("POST", "/api/v1/%61uth/password-reset/request");
            request.setServletPath("/api/v1/auth/password-reset/request");
            var response = new MockHttpServletResponse();
            filter.doFilter(request, response, chain);
            assertThat(response.getStatus()).isEqualTo(i < 20 ? 200 : 429);
            if (i == 20) {
                assertThat(Long.parseLong(response.getHeader("Retry-After"))).isBetween(1L, 60L);
                var body = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getContentAsString());
                assertThat(body.path("code").asText()).isEqualTo("RATE_LIMITED");
                assertThat(body.path("details").isArray()).isTrue();
                assertThat(body.path("violations").isArray()).isTrue();
                assertThat(body.path("requestId").asText()).isEqualTo(response.getHeader("X-Request-ID"));
            }
        }
        verify(chain, times(20)).doFilter(any(), any());
    }
}
