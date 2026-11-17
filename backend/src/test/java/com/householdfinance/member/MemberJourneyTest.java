package com.householdfinance.member;

import com.fasterxml.jackson.databind.*;
import com.householdfinance.access.AccessContext;
import com.householdfinance.workspace.WorkspaceFilter;
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

@SpringBootTest(properties={"workspace.password=synthetic-member-owner-password","spring.datasource.url=jdbc:h2:mem:member-journey;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
class MemberJourneyTest {
  static class MutableClock extends Clock {
    Instant time=Instant.now();public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return this;}public Instant instant(){return time;}
  }
  @org.springframework.boot.test.context.TestConfiguration static class Time {
    @org.springframework.context.annotation.Bean MutableClock memberClock(){return new MutableClock();}
  }
  @Autowired MutableClock clock;
  static final String PASSWORD="synthetic-member-owner-password",MEMBER="synthetic-reader-private-password";
  @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;@Autowired WorkspaceFilter sessions;@Autowired MemberService service;
  Cookie owner,reader;String today;String account;
  @BeforeEach void reset()throws Exception{
    clock.time=clock.time.plusSeconds(61);
    sessions.revokeAll();for(String t:List.of("MEMBER_RECORDS","CASH_POSITION_LINES","CASH_POSITION_SNAPSHOTS","LEDGER_AUDIT","LEDGER_ENTRIES","LEDGER_ACCOUNTS","FORECAST_PLANS","DECISION_PLANS","ACCESS_AUDIT","HOUSEHOLD_USERS"))jdbc.update("DELETE FROM "+t);
    today=LocalDate.now(ZoneId.of("Europe/Dublin")).toString();owner=login("owner",PASSWORD);reader=enrol("member","READER");
  }
  MockHttpServletRequestBuilder request(String method,String path,Cookie cookie,Object body)throws Exception{
    if(method.equals("PUT")&&path.startsWith("/api/member/purchases/")&&body instanceof Map<?,?> fields&&!fields.containsKey("reportToken")){
      var supplied=new HashMap<Object,Object>(fields);supplied.put("reportToken",call("GET","/api/member/report",cookie,null,200).path("token").asText());body=supplied;
    }
    var r=switch(method){case "POST"->post(path);case "PUT"->put(path);default->get(path);};r.with(v->{v.setRemoteAddr("127.0.0.1");v.setServerName("localhost");return v;}).header("X-Workspace-Request","1");if(cookie!=null)r.cookie(cookie);if(body!=null)r.contentType("application/json").content(body instanceof String?(String)body:json.writeValueAsString(body));return r;
  }
  JsonNode call(String method,String path,Cookie c,Object body,int status)throws Exception{
    var r=mvc.perform(request(method,path,c,body)).andExpect(status().is(status)).andReturn().getResponse();return r.getContentAsString().isBlank()?json.nullNode():json.readTree(r.getContentAsString());
  }
  Cookie login(String name,String password)throws Exception{return mvc.perform(request("POST","/api/workspace/unlock",null,Map.of("username",name,"password",password))).andExpect(status().isNoContent()).andReturn().getResponse().getCookie("workspace_session");}
  Cookie enrol(String name,String...roles)throws Exception{
    var invite=call("POST","/api/access/invitations",owner,Map.of("username",name),200);
    call("POST","/api/access/accept",null,Map.of("code",invite.path("code").asText(),"password",MEMBER,"accepted",true),200);
    var users=call("GET","/api/access/users",owner,null,200);JsonNode user=null;for(var u:users)if(u.path("username").asText().equals(name))user=u;
    call("PUT","/api/access/users/"+user.path("id").asText(),owner,Map.of("revision",user.path("revision").asLong(),"roles",roles,"state","ACTIVE"),200);return login(name,MEMBER);
  }
  JsonNode createAccount(String name,String amount,boolean shared)throws Exception{
    var a=call("POST","/api/ledger/accounts",owner,Map.of("name",name,"kind","CASH","openingDate",today,"openingEuro",amount,"source","Synthetic evidence"),200);
    if(shared)a=call("PUT","/api/ledger/accounts/"+a.path("id").asText()+"/sharing",owner,Map.of("revision",a.path("revision").asLong(),"shared",true),200);return a;
  }
  JsonNode currentAccount()throws Exception{for(var a:call("GET","/api/ledger?asOf="+today,owner,null,200).path("accounts"))if(a.path("id").asText().equals(account))return a;throw new AssertionError();}
  void reconcile(String amount)throws Exception{call("POST","/api/ledger/accounts/"+account+"/reconcile?asOf="+today,owner,Map.of("revision",currentAccount().path("revision").asLong(),"statementEuro",amount,"source","Synthetic statement","commit",true,"acceptAdjustment",false),200);}
  void reopen()throws Exception{call("POST","/api/ledger/accounts/"+account+"/reopen",owner,Map.of("revision",currentAccount().path("revision").asLong(),"reason","Synthetic new evidence requires explicit reopening"),200);}
  void saveForecastAndPublish()throws Exception{
    var existing=call("GET","/api/forecast",owner,null,200);long revision=existing.path("plan").isNull()?0:existing.path("plan").path("revision").asLong();
    var plan=call("POST","/api/forecast",owner,Map.of("revision",revision,"name","Synthetic shared plan","baseline",Map.of("mode","SHARED_LEDGER","date",today),"endDate",LocalDate.parse(today).plusDays(14).toString(),"reserveEuro","100.00","events",List.of(Map.of("id",UUID.randomUUID().toString(),"date",LocalDate.parse(today).plusDays(3).toString(),"label","Synthetic bill","direction","EXPENSE","certainty","EXPECTED","amountEuro","200.00","source","Synthetic reviewed expectation"),Map.of("id",UUID.randomUUID().toString(),"date",LocalDate.parse(today).plusDays(2).toString(),"label","Synthetic income","direction","INCOME","certainty","EXPECTED","amountEuro","9000.00","source","Synthetic projection")),"acknowledged",true),200);
    var report=call("GET","/api/member/report",owner,null,200);call("POST","/api/member/publication",owner,Map.of("revision",report.path("boardRevision").asLong(),"forecastRevision",plan.path("plan").path("revision").asLong(),"acknowledged",true),200);
  }
  void seed()throws Exception{
    var a=createAccount("Synthetic shared cash","1000.00",true);account=a.path("id").asText();
    call("POST","/api/ledger/accounts/"+account+"/activity",owner,Map.of("revision",a.path("revision").asLong(),"row",Map.of("reference","pending-one","date",today,"description","Synthetic pending debit","amountEuro","-20.00","status","PENDING","kind","ACTIVITY"),"source","Synthetic statement","acknowledgedOverlap",true),200);
    reconcile("1000.00");saveForecastAndPublish();reader=login("member",MEMBER);
  }
  Map<String,Object> draft(Cookie c,String id,String amount,boolean shared)throws Exception{return Map.of("id",id,"title","Synthetic purchase","category","Home","amountEuro",amount,"date",today,"sharedRequest",shared,"reportToken",call("GET","/api/member/report",c,null,200).path("token").asText(),"acknowledged",true);}
  JsonNode newPurchase(Cookie c,String amount,boolean shared)throws Exception{return call("POST","/api/member/purchases",c,draft(c,UUID.randomUUID().toString(),amount,shared),200).path("purchases").get(0);}
  @Test void unknownIsNotZeroAndAdminIsNotFinancialAccess()throws Exception{
    var report=call("GET","/api/member/report",reader,null,200);assertTrue(report.path("spendingRoomMinor").isNull());assertTrue(report.path("recordedCashMinor").isNull());assertEquals("NO_PUBLISHED_FORECAST",report.path("evidenceState").asText());
    var admin=enrol("adminonly","ADMIN");mvc.perform(request("GET","/api/member/export",admin,null)).andExpect(status().isForbidden());mvc.perform(request("GET","/api/member/report",null,null)).andExpect(status().isUnauthorized());
    mvc.perform(request("POST","/api/member/publication",reader,Map.of("revision",0,"forecastRevision",0,"acknowledged",true))).andExpect(status().isForbidden());
    AccessContext.clear();assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.get());
  }
  @Test void reportUsesSharedReviewedCashAndExcludesFutureIncomeAndPrivateChanges()throws Exception{
    seed();var report=call("GET","/api/member/report",reader,null,200);assertEquals(68000,report.path("spendingRoomMinor").asLong());assertEquals(20000,report.path("commitmentsMinor").asLong());assertEquals("REVIEWED",report.path("evidenceState").asText());
    var token=report.path("token").asText();createAccount("Synthetic private must not leak","321.09",false);
    var exported=call("GET","/api/member/export",reader,null,200);assertEquals(token,exported.path("token").asText());assertFalse(exported.toString().contains("32109"));assertFalse(exported.toString().contains("must not leak"));
    var forecast=call("GET","/api/forecast",owner,null,200).path("plan");assertTrue(forecast.path("currentDecisionReady").asBoolean());assertEquals("SHARED_LEDGER",forecast.path("inputs").path("baseline").path("mode").asText());
    saveForecastAndPublish();assertEquals(68000,call("GET","/api/member/report",reader,null,200).path("spendingRoomMinor").asLong());
    assertEquals("UNAVAILABLE: complete categorised period coverage has not been established",report.path("trendState").asText());
  }
  @Test void privateForecastCannotBePublished()throws Exception{
    var a=createAccount("Synthetic private","100.00",false);account=a.path("id").asText();reconcile("100.00");
    var f=call("POST","/api/forecast",owner,Map.of("revision",0,"name","Private forecast label","baseline",Map.of("mode","LEDGER","date",today),"endDate",LocalDate.parse(today).plusDays(7).toString(),"reserveEuro","0","events",List.of(),"acknowledged",true),200);
    call("POST","/api/member/publication",owner,Map.of("revision",0,"forecastRevision",f.path("plan").path("revision").asLong(),"acknowledged",true),400);
    assertFalse(call("GET","/api/member/export",reader,null,200).toString().contains("Private forecast label"));
  }
  @Test void ordinaryLedgerForecastIsRejectedEvenWhenEveryCashAccountIsShared()throws Exception{
    seed();var existing=call("GET","/api/forecast",owner,null,200).path("plan");
    var f=call("POST","/api/forecast",owner,Map.of("revision",existing.path("revision").asLong(),"name","Ordinary ledger","baseline",Map.of("mode","LEDGER","date",today),"endDate",LocalDate.parse(today).plusDays(7).toString(),"reserveEuro","0","events",List.of(),"acknowledged",true),200);
    call("POST","/api/member/publication",owner,Map.of("revision",1,"forecastRevision",f.path("plan").path("revision").asLong(),"acknowledged",true),400);
  }
  @Test void optimisticPersistenceFailureReturnsConflictWithoutPrivateDetails()throws Exception{
    var mock=org.mockito.Mockito.mock(MemberService.class);
    org.mockito.Mockito.when(mock.get()).thenThrow(new org.springframework.dao.OptimisticLockingFailureException("Private persistence details"));
    var standalone=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(new MemberController(mock,json)).build();
    var response=standalone.perform(get("/api/member")).andExpect(status().isConflict()).andReturn().getResponse().getContentAsString();
    assertTrue(response.contains("refresh"));assertFalse(response.contains("Private persistence"));
  }
  @Test void readerCanPlanWithoutAdminAndRetriesDoNotDuplicateOrOverwrite()throws Exception{
    seed();var id=UUID.randomUUID().toString();var input=draft(reader,id,"700.00",false);
    var saved=call("POST","/api/member/purchases",reader,input,200);assertEquals(-2000,saved.path("report").path("spendingRoomMinor").asLong());assertEquals("EXCEEDS_REVIEWED_ROOM",saved.path("purchases").get(0).path("assessment").asText());
    assertEquals(1,call("POST","/api/member/purchases",reader,input,200).path("purchases").size());
    assertEquals(0,call("GET","/api/member",owner,null,200).path("purchases").size());
    var stale=new HashMap<>(input);stale.put("id",UUID.randomUUID().toString());call("POST","/api/member/purchases",reader,stale,409);
    call("PUT","/api/member/purchases/"+id,reader,Map.of("revision",1,"action","CANCEL"),200);call("PUT","/api/member/purchases/"+id,reader,Map.of("revision",1,"action","BOUGHT"),409);
    call("GET","/api/member/purchases/"+id+"/history",owner,null,400);
    var history=call("GET","/api/member/purchases/"+id+"/history",reader,null,200);assertEquals(2,history.size());assertFalse(history.get(0).path("evidenceToken").asText().isBlank());
  }
  @Test void sharedRequestsNeedReceivingEditorDecisionAndReserveOnce()throws Exception{
    seed();var p=newPurchase(reader,"80.00",true);String id=p.path("id").asText();
    assertEquals(68000,call("GET","/api/member/report",owner,null,200).path("spendingRoomMinor").asLong());
    mvc.perform(request("PUT","/api/member/purchases/"+id,reader,Map.of("revision",1,"action","APPROVE","reason","Self approval"))).andExpect(status().isForbidden());
    var other=enrol("another","EDITOR");call("PUT","/api/member/purchases/"+id,other,Map.of("revision",1,"action","APPROVE","reason","Not the reviewer"),400);
    var approved=call("PUT","/api/member/purchases/"+id,owner,Map.of("revision",1,"action","APPROVE","reason","Additional reviewed cost"),200);assertEquals(60000,approved.path("report").path("spendingRoomMinor").asLong());
    var ownReport=call("GET","/api/member/report",reader,null,200);assertEquals(60000,ownReport.path("spendingRoomMinor").asLong());assertEquals(8000,ownReport.path("purchaseReservationsMinor").asLong());
    assertEquals(60000,call("GET","/api/member/report",other,null,200).path("spendingRoomMinor").asLong());assertEquals(0,call("GET","/api/member",other,null,200).path("purchases").size());
    call("PUT","/api/member/purchases/"+id,owner,Map.of("revision",1,"action","DECLINE","reason","Stale refusal"),409);
    assertEquals("APPROVED",call("GET","/api/member",reader,null,200).path("purchases").get(0).path("purchase").path("state").asText());
  }
  @Test void actualMatchReleasesOnlyItsOverlayAndShowsVarianceWithoutLedgerMutation()throws Exception{
    seed();var p=newPurchase(reader,"80.00",false);String id=p.path("id").asText();
    reopen();
    call("PUT","/api/member/purchases/"+id,reader,Map.of("revision",1,"action","BOUGHT"),200);
    call("POST","/api/ledger/accounts/"+account+"/activity",owner,Map.of("revision",currentAccount().path("revision").asLong(),"row",Map.of("reference","actual-one","date",today,"description","Synthetic actual purchase","amountEuro","-85.00","status","BOOKED","kind","ACTIVITY"),"source","Synthetic statement","acknowledgedOverlap",true),200);
    assertTrue(call("GET","/api/member/report",reader,null,200).path("spendingRoomMinor").isNull());
    String entry=call("GET","/api/ledger/accounts/"+account+"/activity",reader,null,200).get(0).path("id").asText();
    // Find actual independent of reference sorting.
    for(var e:call("GET","/api/ledger/accounts/"+account+"/activity",reader,null,200))if(e.path("reference").asText().equals("actual-one"))entry=e.path("id").asText();
    var matched=call("PUT","/api/member/purchases/"+id,reader,Map.of("revision",2,"action","MATCH","accountId",account,"entryId",entry),200);var v=matched.path("purchases").get(0);assertEquals(8500,v.path("actualMinor").asLong());assertEquals(500,v.path("varianceMinor").asLong());assertEquals(0,matched.path("report").path("purchaseReservationsMinor").asLong());assertEquals(91500,currentAccount().path("bookedMinor").asLong());
    reconcile("915.00");saveForecastAndPublish();assertEquals(59500,call("GET","/api/member/report",reader,null,200).path("spendingRoomMinor").asLong());
    var second=newPurchase(reader,"20.00",false);call("PUT","/api/member/purchases/"+second.path("id").asText(),reader,Map.of("revision",1,"action","BOUGHT"),200);
    call("PUT","/api/member/purchases/"+second.path("id").asText(),reader,Map.of("revision",2,"action","MATCH","accountId",account,"entryId",entry),400);
    reopen();call("PUT","/api/ledger/accounts/"+account+"/activity/"+entry,owner,Map.of("revision",currentAccount().path("revision").asLong(),"row",Map.of("reference","actual-one","date",today,"description","Corrected actual purchase","amountEuro","-86.00","status","BOOKED","kind","ACTIVITY"),"reason","Synthetic correction"),200);
    var changed=call("GET","/api/member",reader,null,200);JsonNode original=null;for(var item:changed.path("purchases"))if(item.path("id").asText().equals(id))original=item;
    assertNotNull(original);assertFalse(original.path("matchValid").asBoolean());assertTrue(original.path("actualMinor").isNull());assertEquals(10000,changed.path("report").path("purchaseReservationsMinor").asLong());
    call("PUT","/api/member/purchases/"+id,reader,Map.of("revision",3,"action","MATCH","accountId",account,"entryId",entry),200);
    assertEquals(2000,call("GET","/api/member/report",reader,null,200).path("purchaseReservationsMinor").asLong());
  }
  @Test void withdrawalHidesPublicationRequestsHistoryAndExport()throws Exception{
    seed();var p=newPurchase(reader,"15.00",true);String id=p.path("id").asText();
    call("PUT","/api/ledger/accounts/"+account+"/sharing",owner,Map.of("revision",currentAccount().path("revision").asLong(),"shared",false),200);reader=login("member",MEMBER);
    var state=call("GET","/api/member",reader,null,200);assertTrue(state.path("report").path("board").isNull());assertTrue(state.path("report").path("spendingRoomMinor").isNull());assertEquals(0,state.path("purchases").size());assertEquals(0,state.path("notifications").size());
    call("GET","/api/member/purchases/"+id+"/history",reader,null,400);assertFalse(call("GET","/api/member/export",reader,null,200).toString().contains("Synthetic shared plan"));
    assertTrue(call("GET","/api/forecast",owner,null,200).path("plan").isNull());
  }
  @Test void preferencesAndNotificationsArePersonalAndRevisionProtected()throws Exception{
    seed();newPurchase(reader,"5.00",false);
    var saved=call("PUT","/api/member/preferences",reader,Map.of("revision",0,"categories",List.of("Garden","Travel"),"dashboard","COMPACT","notifications",true,"markSeen",true),200);assertEquals(0,saved.path("notifications").size());assertEquals("Garden",saved.path("preferences").path("values").path("categories").get(0).asText());
    assertEquals("DETAIL",call("GET","/api/member",owner,null,200).path("preferences").path("values").path("dashboard").asText());
    call("PUT","/api/member/preferences",reader,Map.of("revision",0,"categories",List.of("Bad stale"),"dashboard","DETAIL","notifications",true,"markSeen",false),409);
  }
  @Test void pendingOrDeclinedRequestsNeverPreventRecordingAnActualPurchase()throws Exception{
    seed();var p=newPurchase(reader,"30.00",true);String id=p.path("id").asText();
    var bought=call("PUT","/api/member/purchases/"+id,reader,Map.of("revision",1,"action","BOUGHT"),200);assertEquals("BOUGHT",bought.path("purchases").get(0).path("purchase").path("state").asText());
    assertEquals(68000,call("GET","/api/member/report",owner,null,200).path("spendingRoomMinor").asLong());
    call("PUT","/api/member/purchases/"+id,owner,Map.of("revision",2,"action","DECLINE","reason","Not agreed as shared"),200);
    var result=call("GET","/api/member",reader,null,200).path("purchases").get(0).path("purchase");assertEquals("BOUGHT",result.path("state").asText());assertEquals("DECLINED",result.path("requestDecision").asText());
    assertEquals(65000,call("GET","/api/member/report",reader,null,200).path("spendingRoomMinor").asLong());
  }
  @Test void concurrentDecisionsAcceptOnlyOneRevision()throws Exception{
    seed();var p=newPurchase(reader,"10.00",false);String id=p.path("id").asText();var ready=new java.util.concurrent.CountDownLatch(2);var go=new java.util.concurrent.CountDownLatch(1);var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
    try{
      var cancel=pool.submit(()->{ready.countDown();go.await();return mvc.perform(request("PUT","/api/member/purchases/"+id,reader,Map.of("revision",1,"action","CANCEL"))).andReturn().getResponse().getStatus();});
      var buy=pool.submit(()->{ready.countDown();go.await();return mvc.perform(request("PUT","/api/member/purchases/"+id,reader,Map.of("revision",1,"action","BOUGHT"))).andReturn().getResponse().getStatus();});
      assertTrue(ready.await(5,java.util.concurrent.TimeUnit.SECONDS));go.countDown();var statuses=new ArrayList<>(List.of(cancel.get(10,java.util.concurrent.TimeUnit.SECONDS),buy.get(10,java.util.concurrent.TimeUnit.SECONDS)));Collections.sort(statuses);assertEquals(List.of(200,409),statuses);
      assertEquals(2,call("GET","/api/member/purchases/"+id+"/history",reader,null,200).size());
    }finally{pool.shutdownNow();}
  }
  @Test void staleFinancialEvidenceCannotBeUsedForAnOtherwiseCurrentPurchaseAction()throws Exception{
    seed();var p=newPurchase(reader,"10.00",false);String id=p.path("id").asText();String stale=call("GET","/api/member/report",reader,null,200).path("token").asText();
    reopen();
    call("POST","/api/ledger/accounts/"+account+"/activity",owner,Map.of("revision",currentAccount().path("revision").asLong(),"row",Map.of("reference","new-evidence","date",today,"description","Synthetic changed evidence","amountEuro","-1.00","status","PENDING","kind","ACTIVITY"),"source","Synthetic update","acknowledgedOverlap",true),200);
    call("PUT","/api/member/purchases/"+id,reader,Map.of("revision",1,"action","BOUGHT","reportToken",stale),409);
    var current=call("PUT","/api/member/purchases/"+id,reader,Map.of("revision",1,"action","BOUGHT"),200);assertTrue(current.path("report").path("spendingRoomMinor").isNull());assertEquals("BOUGHT",current.path("purchases").get(0).path("purchase").path("state").asText());
  }
  @Test void moneyRevisionAndConsentTypesAreStrictAndFailuresKeepData()throws Exception{
    seed();var input=draft(reader,UUID.randomUUID().toString(),"1.001",false);call("POST","/api/member/purchases",reader,input,400);
    var bad=new HashMap<>(input);bad.put("amountEuro",1.25);call("POST","/api/member/purchases",reader,bad,400);bad.put("amountEuro","1.25");bad.put("acknowledged",false);call("POST","/api/member/purchases",reader,bad,400);
    call("PUT","/api/member/preferences",reader,"{\"revision\":0.5,\"categories\":[\"Home\"],\"dashboard\":\"DETAIL\",\"notifications\":true,\"markSeen\":false}",400);
    assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM MEMBER_RECORDS WHERE KIND='PURCHASE'",Integer.class));
  }
}
