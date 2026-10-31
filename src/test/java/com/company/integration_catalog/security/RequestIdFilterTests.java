package com.company.integration_catalog.security;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestIdFilterTests {
    @Test void requestIdIsAvailableDuringTheRequestAndMdcIsCleanedAfterFailure() {
        var request = new MockHttpServletRequest();
        request.addHeader("X-Request-ID", "untrusted");
        var response = new MockHttpServletResponse();
        MDC.remove("requestId");
        assertThatThrownBy(() -> new RequestIdFilter().doFilter(request, response, (input, output) -> {
            assertThat(MDC.get("requestId")).isEqualTo(RequestIdFilter.requestId(request));
            assertThat(UUID.fromString(MDC.get("requestId"))).isNotNull();
            throw new IllegalStateException("test failure");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(MDC.get("requestId")).isNull();
        assertThat(response.getHeader("X-Request-ID")).isEqualTo(RequestIdFilter.requestId(request)).isNotEqualTo("untrusted");
    }
}
