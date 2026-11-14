package com.householdfinance.workspace;

import java.time.*;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import static org.junit.jupiter.api.Assertions.*;

class WorkspaceFilterTest {
    static class MutableClock extends Clock {
        Instant now=Instant.parse("2026-11-14T14:00:00Z");
        public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return this;}public Instant instant(){return now;}
    }
    @Test void failClosedRateLimitExpirationAndRestart() throws Exception {
        var request=new MockHttpServletRequest();var response=new MockHttpServletResponse();
        assertEquals(503,new WorkspaceFilter("","",Clock.systemUTC()).login("whatever",request,response));
        var clock=new MutableClock();var filter=new WorkspaceFilter("synthetic-local-workspace-password","",clock);
        for(int i=0;i<5;i++)assertEquals(401,filter.login("wrong",request,response));
        assertEquals(429,filter.login("synthetic-local-workspace-password",request,response));
        clock.now=clock.now.plusSeconds(61);response=new MockHttpServletResponse();
        assertEquals(204,filter.login("synthetic-local-workspace-password",request,response));
        String token=response.getHeader("Set-Cookie").split(";",2)[0].split("=",2)[1];request.setCookies(new Cookie("workspace_session",token));
        assertTrue(filter.authenticated(request));
        clock.now=clock.now.plusSeconds(1800);assertFalse(filter.authenticated(request));
        assertFalse(new WorkspaceFilter("synthetic-local-workspace-password","",clock).authenticated(request));
    }
    @Test void unconfiguredFinancialAccessIsUnavailableEvenWithForgedCookie() throws Exception {
        var filter=new WorkspaceFilter("short","",Clock.systemUTC());var req=new MockHttpServletRequest("GET","/planner");req.setServerName("localhost");req.setCookies(new Cookie("workspace_session","forged"));
        var response=new MockHttpServletResponse();filter.doFilter(req,response,new MockFilterChain());assertEquals(503,response.getStatus());
    }
}
