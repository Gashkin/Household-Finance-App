package com.householdfinance.intake;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import static org.junit.jupiter.api.Assertions.*;

class IntakeAccessFilterTest {
    @Test void unknownLengthBodyIsBoundedBeforeControllerParsing() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/evidence-intake/drafts") {
            @Override public long getContentLengthLong() { return -1; }
            @Override public int getContentLength() { return -1; }
        };
        request.addHeader("X-Intake-Key", "synthetic-test-access-0000");
        request.setContent(new byte[32769]);
        var response = new MockHttpServletResponse(); var chain = new MockFilterChain();
        new IntakeAccessFilter("synthetic-test-access-0000").doFilter(request, response, chain);
        assertEquals(413, response.getStatus()); assertNull(chain.getRequest());
    }
    @Test void boundedBodyIsReplayedUnchangedToController() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/evidence-intake/drafts");
        request.addHeader("X-Intake-Key", "synthetic-test-access-0000");
        byte[] body = "{\"source\":\"€10\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        request.setContent(body);
        var response = new MockHttpServletResponse(); var chain = new MockFilterChain();
        new IntakeAccessFilter("synthetic-test-access-0000").doFilter(request, response, chain);
        assertNotNull(chain.getRequest()); assertArrayEquals(body, chain.getRequest().getInputStream().readAllBytes());
        assertEquals("no-store", response.getHeader("Cache-Control"));
    }
}
