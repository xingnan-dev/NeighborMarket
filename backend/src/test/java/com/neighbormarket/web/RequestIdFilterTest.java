package com.neighbormarket.web;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class RequestIdFilterTest {
    private final RequestIdFilter filter = new RequestIdFilter();

    @Test
    void preservesSafeCallerIdAndMakesItAvailableToResponseAndMdc() throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.HEADER_NAME, "client-request_42");
        var response = new MockHttpServletResponse();
        var observed = new AtomicReference<String>();

        filter.doFilter(request, response, (req, res) -> observed.set(MDC.get(RequestIdFilter.MDC_KEY)));

        assertEquals("client-request_42", response.getHeader(RequestIdFilter.HEADER_NAME));
        assertEquals("client-request_42", observed.get());
        assertNull(MDC.get(RequestIdFilter.MDC_KEY));
    }

    @Test
    void replacesUnsafeCallerIdInsteadOfReflectingIt() throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.HEADER_NAME, "bad\r\nforged-log-entry");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> { });

        String generated = response.getHeader(RequestIdFilter.HEADER_NAME);
        assertNotNull(generated);
        assertNotEquals("bad\r\nforged-log-entry", generated);
        assertTrue(generated.matches("[0-9a-f-]{36}"));
        assertNull(MDC.get(RequestIdFilter.MDC_KEY));
    }
}
