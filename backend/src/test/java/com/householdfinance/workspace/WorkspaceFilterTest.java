package com.householdfinance.workspace;

import java.time.*;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import static org.junit.jupiter.api.Assertions.*;

class WorkspaceFilterTest {
    @Test void sensitiveChangesRequireRecentPasswordWhileOrdinaryReadsRemainAvailable() throws Exception {
        var clock=new MutableClock();
        var users=org.mockito.Mockito.mock(com.householdfinance.access.AccessService.class);
        var actor=new com.householdfinance.access.AccessContext.Actor("owner","owner",java.util.Set.of("ADMIN","EDITOR","READER"),0,true);
        org.mockito.Mockito.when(users.configured()).thenReturn(true);
        org.mockito.Mockito.when(users.authenticate("owner","synthetic-local-workspace-password")).thenReturn(actor);
        org.mockito.Mockito.when(users.active("owner",0)).thenReturn(actor);
        org.mockito.Mockito.when(users.permits(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.eq(actor))).thenReturn(true);
        org.mockito.Mockito.when(users.verifies("owner","synthetic-local-workspace-password")).thenReturn(true);
        var filter=new WorkspaceFilter("synthetic-local-workspace-password","",clock);
        org.springframework.test.util.ReflectionTestUtils.setField(filter,"users",users);
        var login=new MockHttpServletResponse();
        assertEquals(204,filter.login("owner","synthetic-local-workspace-password",new MockHttpServletRequest(),login));
        var cookie=new Cookie("workspace_session",login.getHeader("Set-Cookie").split(";",2)[0].split("=",2)[1]);
        clock.now=clock.now.plusSeconds(300);
        var change=new MockHttpServletRequest("POST","/api/access/invitations");change.setServerName("localhost");change.addHeader("X-Workspace-Request","1");change.setCookies(cookie);
        var denied=new MockHttpServletResponse();filter.doFilter(change,denied,new MockFilterChain());assertEquals(403,denied.getStatus());
        var read=new MockHttpServletRequest("GET","/api/ledger");read.setServerName("localhost");read.setCookies(cookie);
        var allowed=new MockHttpServletResponse();filter.doFilter(read,allowed,new MockFilterChain());assertEquals(200,allowed.getStatus());
        assertFalse(filter.reauthenticate(change,"wrong"));assertFalse(filter.recent(change));
        assertTrue(filter.reauthenticate(change,"synthetic-local-workspace-password"));assertTrue(filter.recent(change));
        var confirmed=new MockHttpServletResponse();filter.doFilter(change,confirmed,new MockFilterChain());assertEquals(200,confirmed.getStatus());
    }
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
    @Test void backgroundContractInboxReadsRespectWorkspaceIdleExpiry() throws Exception {
        var clock = new MutableClock();
        var filter = new WorkspaceFilter("synthetic-local-workspace-password", "", clock);
        var login = new MockHttpServletResponse();
        assertEquals(204, filter.login("synthetic-local-workspace-password", new MockHttpServletRequest(), login));
        var session = new Cookie("workspace_session", login.getHeader("Set-Cookie").split(";", 2)[0].split("=", 2)[1]);

        for (int minute = 1; minute <= 30; minute++) {
            clock.now = clock.now.plusSeconds(60);
            var poll = new MockHttpServletRequest("GET", "/api/contracts/alerts");
            poll.setServerName("localhost");
            poll.setCookies(session);
            var result = new MockHttpServletResponse();
            filter.doFilter(poll, result, new MockFilterChain());
            assertEquals(minute < 30 ? 200 : 401, result.getStatus());
            if (minute == 30) assertEquals("1", result.getHeader("X-Workspace-Locked"));
        }
    }
    @Test void unconfiguredFinancialAccessIsUnavailableEvenWithForgedCookie() throws Exception {
        var filter=new WorkspaceFilter("short","",Clock.systemUTC());var req=new MockHttpServletRequest("GET","/planner");req.setServerName("localhost");req.setCookies(new Cookie("workspace_session","forged"));
        var response=new MockHttpServletResponse();filter.doFilter(req,response,new MockFilterChain());assertEquals(503,response.getStatus());
    }
}
