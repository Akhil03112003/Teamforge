package com.teamforge.platform.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.*;

class RequestLimitsFilterTest {
    private final RequestLimitsFilter filter=new RequestLimitsFilter(new ObjectMapper(),16384,2);
    private MockHttpServletRequest request(String method,String path){var r=new MockHttpServletRequest(method,path);r.setServletPath(path);return r;}
    @Test void rejectsDeclaredOversizeWithoutParsing(){var r=request("POST","/api/v1/auth/register");r.setContent(new byte[16385]);var response=new MockHttpServletResponse();assertThatCode(()->filter.doFilter(r,response,(a,b)->fail("must not enter application"))).doesNotThrowAnyException();assertThat(response.getStatus()).isEqualTo(413);}
    @Test void buffersAndPreservesFormParameters() throws Exception {
        var r=request("POST","/api/v1/auth/login");r.setContentType("application/x-www-form-urlencoded");r.setContent("email=a%40example.test&password=spaces+and%2Bsymbols&_csrf=token".getBytes(StandardCharsets.UTF_8));
        filter.doFilter(r,new MockHttpServletResponse(),(req,res)->{assertThat(req.getParameter("email")).isEqualTo("a@example.test");assertThat(req.getParameter("password")).isEqualTo("spaces and+symbols");assertThat(req.getParameter("_csrf")).isEqualTo("token");});
    }
    @Test void duplicateFormParametersAreRejected() throws Exception {var r=request("POST","/api/v1/auth/login");r.setContentType("application/x-www-form-urlencoded");r.setContent("email=one&email=two".getBytes(StandardCharsets.UTF_8));var out=new MockHttpServletResponse();filter.doFilter(r,out,(a,b)->fail("must reject duplicate fields"));assertThat(out.getStatus()).isEqualTo(400);}
    @Test void malformedFormIsRedacted() throws Exception {var r=request("POST","/api/v1/auth/login");r.setContentType("application/x-www-form-urlencoded");r.setContent("password=private-marker%ZZ".getBytes(StandardCharsets.UTF_8));var out=new MockHttpServletResponse();filter.doFilter(r,out,(a,b)->fail("must reject"));assertThat(out.getStatus()).isEqualTo(400);assertThat(out.getContentAsString()).doesNotContain("private-marker");}
    @Test void loginQueryIsRejectedWithoutEcho() throws Exception {var r=request("POST","/api/v1/auth/login");r.setQueryString("password=private-marker");var out=new MockHttpServletResponse();filter.doFilter(r,out,(a,b)->fail("must reject URL credentials"));assertThat(out.getStatus()).isEqualTo(400);assertThat(out.getContentAsString()).doesNotContain("private-marker");}
    @Test void anonymousBootstrapIsBoundedAndDoesNotTrustForwardingHeaders() throws Exception {for(int i=0;i<3;i++){var r=request("GET","/api/v1/auth/csrf");r.addHeader("X-Forwarded-For","198.51.100."+i);var out=new MockHttpServletResponse();filter.doFilter(r,out,(a,b)->{});assertThat(out.getStatus()).isEqualTo(i<2?200:429);}}
    @Test void existingSessionDoesNotConsumeBootstrapLimit() throws Exception {for(int i=0;i<4;i++){var r=request("GET","/api/v1/auth/csrf");r.setSession(new MockHttpSession());var out=new MockHttpServletResponse();filter.doFilter(r,out,(a,b)->{});assertThat(out.getStatus()).isEqualTo(200);}}
    @Test void invalidBoundsFailFast(){assertThatThrownBy(()->new RequestLimitsFilter(new ObjectMapper(),1,2)).isInstanceOf(IllegalArgumentException.class);assertThatThrownBy(()->new AuthRateLimitFilter(new ObjectMapper(),java.time.Clock.systemUTC(),0)).isInstanceOf(IllegalArgumentException.class);}
}
