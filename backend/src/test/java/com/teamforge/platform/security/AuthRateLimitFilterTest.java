package com.teamforge.platform.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.assertThat;

class AuthRateLimitFilterTest {
    @Test void limitsAuthAttemptsAndKeepsOtherEndpointsAvailable() throws Exception {
        var filter = new AuthRateLimitFilter(new ObjectMapper(), Clock.systemUTC(), 1);
        var request = new MockHttpServletRequest("POST", "/api/v1/auth/register");
        request.setServletPath("/api/v1/auth/register");
        var first = new MockHttpServletResponse();
        filter.doFilter(request, first, (input, output) -> ((MockHttpServletResponse) output).setStatus(201));
        assertThat(first.getStatus()).isEqualTo(201);
        var second = new MockHttpServletResponse();
        filter.doFilter(request, second, (input, output) -> { throw new AssertionError("Throttled request reached the handler"); });
        assertThat(second.getStatus()).isEqualTo(429);
        assertThat(second.getHeader("Retry-After")).isEqualTo("60");
        var health = new MockHttpServletRequest("GET", "/api/v1/health");
        var healthy = new MockHttpServletResponse();
        filter.doFilter(health, healthy, (input, output) -> ((MockHttpServletResponse) output).setStatus(200));
        assertThat(healthy.getStatus()).isEqualTo(200);
    }
}
