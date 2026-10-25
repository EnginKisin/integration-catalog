package com.company.integration_catalog.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import jakarta.servlet.FilterChain;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthRateLimitFilterTests {
    @Test void decodedServletPathCannotBypassRateLimit() throws Exception {
        var filter = new AuthRateLimitFilter();
        FilterChain chain = mock(FilterChain.class);
        for (int i = 0; i < 21; i++) {
            var request = new MockHttpServletRequest("POST", "/api/v1/%61uth/password-reset/request");
            request.setServletPath("/api/v1/auth/password-reset/request");
            var response = new MockHttpServletResponse();
            filter.doFilter(request, response, chain);
            assertThat(response.getStatus()).isEqualTo(i < 20 ? 200 : 429);
        }
        verify(chain, times(20)).doFilter(any(), any());
    }
}
