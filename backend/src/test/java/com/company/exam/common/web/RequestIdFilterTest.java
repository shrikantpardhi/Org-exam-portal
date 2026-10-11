package com.company.exam.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @Test
    void propagatesAValidSuppliedRequestId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/system/info");
        request.addHeader(RequestContext.HEADER, "abc-123_DEF.456");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> observed = new AtomicReference<>();
        FilterChain chain = (req, res) -> observed.set(RequestContext.getRequestId());

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(RequestContext.HEADER)).isEqualTo("abc-123_DEF.456");
        assertThat(observed.get()).isEqualTo("abc-123_DEF.456");
    }

    @Test
    void generatesARequestIdWhenAbsent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/system/info");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
        });

        assertThat(response.getHeader(RequestContext.HEADER))
                .isNotNull()
                .matches("[0-9a-fA-F-]{36}");
    }

    @Test
    void replacesAnUnsafeSuppliedRequestId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/system/info");
        request.addHeader(RequestContext.HEADER, "bad value\ninjected");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
        });

        assertThat(response.getHeader(RequestContext.HEADER)).doesNotContain("injected");
    }

    @Test
    void clearsTheRequestIdAfterTheRequestCompletes() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/system/info");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
        });

        assertThat(RequestContext.getRequestId()).isNull();
    }
}
