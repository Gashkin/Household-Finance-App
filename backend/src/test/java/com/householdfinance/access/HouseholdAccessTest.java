package com.householdfinance.access;

import com.fasterxml.jackson.databind.*;
import com.householdfinance.workspace.*;
import jakarta.servlet.http.Cookie;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"workspace.password=synthetic-access-owner-password","spring.datasource.url=jdbc:h2:mem:household-access;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
class HouseholdAccessTest {
  static class MutableClock extends Clock {
    Instant time=Instant.parse("2026-11-16T12:00:00Z");public java.time.ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(java.time.ZoneId z){return this;}public Instant instant(){return time;}
  }
  @org.springframework.boot.test.context.TestConfiguration static class Time {
    @org.springframework.context.annotation.Bean MutableClock testClock(){return new MutableClock();}
  }
  static final String OWNER="synthetic-access-owner-password",MEMBER="synthetic-member-password-123",NEW="synthetic-replacement-password";
  @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcTemplate jdbc; @Autowired WorkspaceFilter sessions;
  @Autowired MutableClock clock;
  @Autowired com.householdfinance.ledger.LedgerService ledger;
  @Autowired com.householdfinance.service.PlannerService legacy;
  Cookie owner; String today=LocalDate.now(ZoneId.of("Europe/Dublin")).toString();
  @Test void domainEntryPointsFailClosedWithoutIdentityAndRejectReaderWrites() {
    AccessContext.clear();
    assertThrows(org.springframework.web.server.ResponseStatusException.class,()->ledger.position(LocalDate.now()));
    assertThrows(org.springframework.web.server.ResponseStatusException.class,()->legacy.getPlannerData());
    try {
      AccessContext.enter(new AccessContext.Actor("owner","owner",Set.of("READER"),0,true));
      assertThrows(org.springframework.web.server.ResponseStatusException.class,()->ledger.create(new com.householdfinance.ledger.LedgerService.AccountInput("Forbidden","CASH",LocalDate.now(),"1","Synthetic")));
      assertThrows(org.springframework.web.server.ResponseStatusException.class,()->legacy.getPlannerData());
    } finally {AccessContext.clear();}
  }
  @BeforeEach void reset()throws Exception{
    clock.time=clock.time.plusSeconds(61);
    sessions.revokeAll();
    for(String table:List.of("CASH_POSITION_LINES","CASH_POSITION_SNAPSHOTS","LEDGER_AUDIT","LEDGER_ENTRIES","LEDGER_ACCOUNTS","FORECAST_PLANS","DECISION_PLANS","ACCESS_AUDIT","HOUSEHOLD_USERS"))jdbc.update("DELETE FROM "+table);
    owner=login("owner",OWNER);
  }
  MockHttpServletRequestBuilder send(String method,String path,Cookie cookie,Object body)throws Exception{
    var r=switch(method){case "PUT"->put(path);case "GET"->get(path);default->post(path);};
    r.header("X-Workspace-Request","1");if(cookie!=null)r.cookie(cookie);if(body!=null)r.contentType("application/json").content(json.writeValueAsBytes(body));return r;
  }
  JsonNode invoke(String method,String path,Cookie cookie,Object body,int status)throws Exception{
    var r=mvc.perform(send(method,path,cookie,body)).andExpect(status().is(status)).andReturn().getResponse();return r.getContentAsByteArray().length==0?json.nullNode():json.readTree(r.getContentAsByteArray());
  }
  Cookie login(String username,String password)throws Exception{
    var response=mvc.perform(send("POST","/api/workspace/unlock",null,Map.of("username",username,"password",password))).andExpect(status().isNoContent()).andReturn().getResponse();
    return new Cookie("workspace_session",response.getHeader("Set-Cookie").split(";",2)[0].split("=",2)[1]);
  }
  JsonNode user(String name)throws Exception{for(var u:invoke("GET","/api/access/users",owner,null,200))if(u.path("username").asText().equals(name))return u;throw new AssertionError("Missing user");}
  void grant(String name,String... roles)throws Exception{var u=user(name);invoke("PUT","/api/access/users/"+u.path("id").asText(),owner,Map.of("revision",u.path("revision").asLong(),"roles",roles,"state","ACTIVE"),200);}
  Cookie enrol(String name,String...roles)throws Exception{
    var invitation=invoke("POST","/api/access/invitations",owner,Map.of("username",name),200);
    invoke("POST","/api/access/accept",null,Map.of("code",invitation.path("code").asText(),"password",MEMBER,"accepted",true),200);
    grant(name,roles);return login(name,MEMBER);
  }
  JsonNode account(Cookie actor,String name,String amount)throws Exception{return invoke("POST","/api/ledger/accounts",actor,Map.of("name",name,"kind","CASH","openingDate",today,"openingEuro",amount,"source","Synthetic private evidence"),200);}
  JsonNode share(JsonNode a,boolean sharing)throws Exception{return invoke("PUT","/api/ledger/accounts/"+a.path("id").asText()+"/sharing",owner,Map.of("revision",a.path("revision").asLong(),"shared",sharing),200);}
  @Test void allRoleCombinationsUseCapabilitiesNotAdminBypass()throws Exception{
    enrol("combinations");
    for(int bits=0;bits<8;bits++){
      List<String> roles=new ArrayList<>();if((bits&1)!=0)roles.add("ADMIN");if((bits&2)!=0)roles.add("EDITOR");if((bits&4)!=0)roles.add("READER");
      grant("combinations",roles.toArray(String[]::new));Cookie c=login("combinations",MEMBER);
      mvc.perform(send("GET","/api/ledger?asOf="+today,c,null)).andExpect(status().is((bits&6)!=0?200:403));
      mvc.perform(send("GET","/api/access/users",c,null)).andExpect(status().is((bits&1)!=0?200:403));
      mvc.perform(send("POST","/api/ledger/accounts",c,Map.of("name","Own "+bits,"kind","CASH","openingDate",today,"openingEuro","1.00","source","Synthetic"))).andExpect(status().is((bits&2)!=0?200:403));
      mvc.perform(send("POST","/api/workspace/backup",c,Map.of("password","synthetic-archive-passphrase"))).andExpect(status().isForbidden());
      mvc.perform(send("GET","/planner",c,null)).andExpect(status().isForbidden());
    }
  }
  @Test void privateAccountNamesBalancesActivityAndAuditNeverReachOtherAdmin()throws Exception{
    var a=account(owner,"Hidden confidential label","913.57");Cookie c=enrol("readeradmin","READER","ADMIN");
    var position=invoke("GET","/api/ledger?asOf="+today,c,null,200);assertEquals(0,position.path("accounts").size());assertEquals(0,position.path("cashMinor").asLong());
    assertEquals(0,invoke("GET","/api/ledger/shared?asOf="+today,owner,null,200).path("cashMinor").asLong());
    assertFalse(position.toString().contains("Hidden"));assertFalse(position.toString().contains("91357"));
    String path="/api/ledger/accounts/"+a.path("id").asText();
    var hidden=mvc.perform(send("GET",path+"/activity",c,null)).andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
    var absent=mvc.perform(send("GET","/api/ledger/accounts/not-present/activity",c,null)).andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();assertEquals(absent,hidden);
    mvc.perform(send("GET",path+"/history",c,null)).andExpect(status().isBadRequest());
    var shared=share(a,true);mvc.perform(send("GET","/api/access/me",c,null)).andExpect(status().isUnauthorized());c=login("readeradmin",MEMBER);position=invoke("GET","/api/ledger?asOf="+today,c,null,200);assertEquals(1,position.path("accounts").size());assertEquals(91357,position.path("cashMinor").asLong());
    invoke("GET",path+"/history",c,null,200);
    share(shared,false);c=login("readeradmin",MEMBER);assertEquals(0,invoke("GET","/api/ledger?asOf="+today,c,null,200).path("accounts").size());
  }
  @Test void anotherEditorCannotWriteSharedAccountOrReadOwnersPrivatePlans()throws Exception{
    var a=share(account(owner,"Shared household","8.00"),true);Cookie c=enrol("editor","EDITOR");
    mvc.perform(send("PUT","/api/ledger/accounts/"+a.path("id").asText()+"/sharing",c,Map.of("revision",a.path("revision").asLong(),"shared",false))).andExpect(status().isBadRequest());
    var own=account(c,"Editor private account","41.23");assertFalse(invoke("GET","/api/ledger?asOf="+today,owner,null,200).toString().contains("Editor private"));
    mvc.perform(send("GET","/api/ledger/accounts/"+own.path("id").asText()+"/activity",owner,null)).andExpect(status().isBadRequest());
    var plan=invoke("POST","/api/plans",owner,Map.of("title","Owner hidden decision","goal","Synthetic goal","evidence",List.of(),"options",List.of()),200);
    assertEquals(0,invoke("GET","/api/plans",c,null,200).size());mvc.perform(send("GET","/api/plans/"+plan.path("id").asText(),c,null)).andExpect(status().isBadRequest());
  }
  @Test void cashSnapshotsAndDebtEvidenceAreOwnedNotGlobal()throws Exception{
    var snapshot=invoke("POST","/api/cash-position/snapshots",owner,Map.of("asOfDate",today,"sourceNote","Owner confidential cash","verification","REVIEWED","cashBalances",List.of(Map.of("label","Private","amountMinor",10000)),"upcomingCommitments",List.of(),"minimumReserveMinor",0),201);
    Cookie c=enrol("cashreader","EDITOR");mvc.perform(send("GET","/api/cash-position/latest",c,null)).andExpect(status().isNoContent());
    mvc.perform(send("POST","/api/debt-scenarios/report",c,Map.of("balanceMinor",1000,"annualRatePercent","0","monthlyPaymentMinor",100,"extraPaymentMinor",0,"liquidCashMinor",10000,"minimumReserveMinor",0,"cashPositionSnapshotId",snapshot.path("id").asText()))).andExpect(status().isBadRequest());
  }
  @Test void grantsRevocationDisableAndRecoveryInvalidateEveryOldSession()throws Exception{
    Cookie c=enrol("revoke","EDITOR");grant("revoke","READER");mvc.perform(send("GET","/api/access/me",c,null)).andExpect(status().isUnauthorized());
    c=login("revoke",MEMBER);var recovery=invoke("POST","/api/access/recovery-code",c,Map.of(),200).path("code").asText();
    invoke("POST","/api/access/recover",null,Map.of("username","revoke","code",recovery,"password",NEW),200);
    mvc.perform(send("GET","/api/access/me",c,null)).andExpect(status().isUnauthorized());
    invoke("POST","/api/access/recover",null,Map.of("username","revoke","code",recovery,"password",MEMBER),400);
    c=login("revoke",NEW);var u=user("revoke");invoke("PUT","/api/access/users/"+u.path("id").asText(),owner,Map.of("revision",u.path("revision").asLong(),"roles",List.of("READER"),"state","DISABLED"),200);
    mvc.perform(send("GET","/api/access/me",c,null)).andExpect(status().isUnauthorized());
    u=user("revoke");assertEquals("READER",u.path("roles").get(0).asText());
  }
  @Test void invitationIsBoundSingleUseExpiringAndNotAnImplicitRoleGrant()throws Exception{
    var invitation=invoke("POST","/api/access/invitations",owner,Map.of("username","invited"),200);String code=invitation.path("code").asText();
    assertFalse(invoke("GET","/api/access/users",owner,null,200).toString().contains(code));
    assertNotEquals(code,jdbc.queryForObject("SELECT INVITATION_HASH FROM HOUSEHOLD_USERS WHERE USERNAME='invited'",String.class));
    invoke("POST","/api/access/accept",null,Map.of("code",code,"password",MEMBER,"accepted",false),400);
    var replacement=invoke("POST","/api/access/invitations/"+user("invited").path("id").asText()+"/reissue",owner,Map.of(),200);
    invoke("POST","/api/access/accept",null,Map.of("code",code,"password",MEMBER,"accepted",true),400);
    code=replacement.path("code").asText();invoke("POST","/api/access/accept",null,Map.of("code",code,"password",MEMBER,"accepted",true),200);
    Cookie c=login("invited",MEMBER);assertEquals(0,invoke("GET","/api/access/me",c,null,200).path("roles").size());mvc.perform(send("GET","/api/ledger?asOf="+today,c,null)).andExpect(status().isForbidden());
    invoke("POST","/api/access/accept",null,Map.of("code",code,"password",MEMBER,"accepted",true),400);
  }
  @Test void selfEscalationStaleWritesAndLastAdminLossAreRejected()throws Exception{
    var u=user("owner");invoke("PUT","/api/access/users/owner",owner,Map.of("revision",u.path("revision").asLong(),"roles",List.of(),"state","DISABLED"),409);
    enrol("second","ADMIN");Cookie second=login("second",MEMBER);
    u=user("second");invoke("PUT","/api/access/users/"+u.path("id").asText(),owner,Map.of("revision",u.path("revision").asLong(),"roles",List.of(),"state","ACTIVE"),200);
    mvc.perform(send("PUT","/api/access/users/owner",second,Map.of("revision",0,"roles",List.of(),"state","ACTIVE"))).andExpect(status().isUnauthorized());
    assertEquals(1L,jdbc.queryForObject("SELECT COUNT(*) FROM HOUSEHOLD_USERS WHERE STATE='ACTIVE' AND ROLES LIKE '%ADMIN%'",Long.class));
    assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM ACCESS_AUDIT WHERE OUTCOME='REJECTED_SELF'",Long.class)>0);
  }
  @Test void unknownAndKnownLoginFailuresAreGenericAndDontLockOtherPeople()throws Exception{
    enrol("limited","READER");
    for(int i=0;i<5;i++)mvc.perform(send("POST","/api/workspace/unlock",null,Map.of("username","limited","password","incorrect"))).andExpect(status().isUnauthorized());
    mvc.perform(send("POST","/api/workspace/unlock",null,Map.of("username","limited","password",MEMBER))).andExpect(status().isTooManyRequests());
    assertNotNull(login("owner",OWNER));
    mvc.perform(send("POST","/api/workspace/unlock",null,Map.of("username","missing","password",MEMBER))).andExpect(status().isUnauthorized());
  }
  @Test void originsPathsAndReadOnlyEndpointsCannotBypassEntitlements()throws Exception{
    Cookie c=enrol("bypass","READER");
    mvc.perform(send("POST","/api/access/invitations",owner,Map.of("username","crosssite")).header("Origin","https://evil.example")).andExpect(status().isForbidden());
    for(String path:List.of("/imports/history","/migration/history/id","/reports/monthly-overview","/api/banking/status","/api/bank-sandbox/status","/api/evidence-intake/capabilities","/api/workspace/records"))mvc.perform(send("GET",path,c,null)).andExpect(status().isForbidden());
    mvc.perform(send("POST","/api/access/reauth",c,Map.of("password",MEMBER))).andExpect(status().isNoContent());
  }
  @Test void simultaneousAdministratorChangesCannotRemoveEveryAdmin()throws Exception{
    Cookie second=enrol("concurrent","ADMIN");var other=user("concurrent");
    var ready=new java.util.concurrent.CountDownLatch(2);var go=new java.util.concurrent.CountDownLatch(1);
    var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
    try{
      var first=pool.submit(()->{ready.countDown();go.await();return mvc.perform(send("PUT","/api/access/users/"+other.path("id").asText(),owner,Map.of("revision",other.path("revision").asLong(),"roles",List.of(),"state","DISABLED"))).andReturn().getResponse().getStatus();});
      var secondResult=pool.submit(()->{ready.countDown();go.await();return mvc.perform(send("PUT","/api/access/users/owner",second,Map.of("revision",0,"roles",List.of(),"state","DISABLED"))).andReturn().getResponse().getStatus();});
      assertTrue(ready.await(5,java.util.concurrent.TimeUnit.SECONDS));go.countDown();var codes=new ArrayList<>(List.of(first.get(10,java.util.concurrent.TimeUnit.SECONDS),secondResult.get(10,java.util.concurrent.TimeUnit.SECONDS)));Collections.sort(codes);assertEquals(List.of(200,401),codes);
      assertEquals(1L,jdbc.queryForObject("SELECT COUNT(*) FROM HOUSEHOLD_USERS WHERE STATE='ACTIVE' AND ROLES LIKE '%ADMIN%'",Long.class));
    }finally{pool.shutdownNow();}
  }
  @Test void expiredRevokedInvitationsCanBeReissuedWithoutGrantingAccess()throws Exception{
    var invitation=invoke("POST","/api/access/invitations",owner,Map.of("username","expires"),200);String id=user("expires").path("id").asText();
    clock.time=clock.time.plus(Duration.ofDays(8));invoke("POST","/api/access/accept",null,Map.of("code",invitation.path("code").asText(),"password",MEMBER,"accepted",true),400);
    var replacement=invoke("POST","/api/access/invitations/"+id+"/reissue",owner,Map.of(),200);
    invoke("POST","/api/access/invitations/"+id+"/revoke",owner,Map.of(),200);invoke("POST","/api/access/accept",null,Map.of("code",replacement.path("code").asText(),"password",MEMBER,"accepted",true),400);
    replacement=invoke("POST","/api/access/invitations/"+id+"/reissue",owner,Map.of(),200);invoke("POST","/api/access/accept",null,Map.of("code",replacement.path("code").asText(),"password",MEMBER,"accepted",true),200);assertEquals(0,user("expires").path("roles").size());
  }
  @Test void withdrawalAlsoProtectsCopiedForecastCashDecisionAndPublicationEvidence()throws Exception{
    var a=share(account(owner,"Shared source to withdraw","100.00"),true);String id=a.path("id").asText();
    invoke("POST","/api/ledger/accounts/"+id+"/reconcile?asOf="+today,owner,Map.of("revision",a.path("revision").asLong(),"statementEuro","100.00","source","Synthetic statement","commit",true,"acceptAdjustment",false),200);
    Cookie c=enrol("derived","EDITOR");
    var forecast=Map.of("revision",0,"name","Shared derived plan","baseline",Map.of("mode","LEDGER","date",today),"endDate",LocalDate.parse(today).plusDays(7).toString(),"reserveEuro","0","events",List.of(),"acknowledged",true);
    invoke("POST","/api/forecast",c,forecast,200);
    var snapshot=invoke("POST","/api/ledger/publish",c,Map.of("requestId",UUID.randomUUID().toString(),"asOf",today,"revisions",Map.of(id,a.path("revision").asLong()),"reserveEuro","0","commitments",List.of(),"acknowledged",true),200);
    var debt=Map.of("balanceMinor",1000,"annualRatePercent","0","monthlyPaymentMinor",100,"extraPaymentMinor",0,"liquidCashMinor",10000,"minimumReserveMinor",0,"cashPositionSnapshotId",snapshot.path("id").asText());
    var plan=invoke("POST","/api/plans/from-debt",c,debt,200);
    share(a,false);c=login("derived",MEMBER);
    var hidden=invoke("GET","/api/forecast",c,null,200);assertTrue(hidden.path("plan").isNull());assertEquals(1,hidden.path("hiddenPlanRevision").asLong());assertFalse(hidden.toString().contains("10000"));
    mvc.perform(send("GET","/api/cash-position/latest",c,null)).andExpect(status().isNoContent());assertEquals(0,invoke("GET","/api/plans",c,null,200).size());assertEquals(0,invoke("GET","/api/ledger/history",c,null,200).size());
    mvc.perform(send("GET","/api/plans/"+plan.path("id").asText(),c,null)).andExpect(status().isBadRequest());mvc.perform(send("POST","/api/debt-scenarios/report",c,debt)).andExpect(status().isBadRequest());
    invoke("POST","/api/forecast",c,Map.of("revision",1,"name","Replacement personal evidence","baseline",Map.of("mode","MANUAL","date",today,"cashEuro","2.00","pendingEuro","0","source","Synthetic new evidence"),"endDate",LocalDate.parse(today).plusDays(7).toString(),"reserveEuro","0","events",List.of(),"acknowledged",true),200);
  }
}
