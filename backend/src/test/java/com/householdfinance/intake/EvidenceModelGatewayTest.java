package com.householdfinance.intake;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class EvidenceModelGatewayTest {
    @Test void consentRedactionStructuredWireAndBudgetUseLocalStubOnly() throws Exception {
        AtomicInteger calls = new AtomicInteger(); AtomicReference<String> request = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        ObjectMapper mapper = new ObjectMapper();
        server.createContext("/extract", exchange -> {
            calls.incrementAndGet(); request.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
            String facts = "{\"facts\":[{\"lineNumber\":1,\"kind\":\"CASH\",\"label\":\"Account\",\"amountEuro\":\"10\"}]}";
            byte[] response = mapper.writeValueAsBytes(java.util.Map.of("choices",java.util.List.of(java.util.Map.of("message",java.util.Map.of("content",facts)))));
            exchange.sendResponseHeaders(200,response.length); exchange.getResponseBody().write(response); exchange.close();
        }); server.start();
        try {
            var gateway = new EvidenceModelGateway(mapper,true,"http://127.0.0.1:"+server.getAddress().getPort()+"/extract","demo-model","",1);
            assertTrue(gateway.extract("Account EUR 10 email@example.com 123456789",false).isEmpty()); assertEquals(0,calls.get());
            assertEquals("10",gateway.extract("Account EUR 10 email@example.com 123456789",true).orElseThrow().getFirst().amountEuro());
            assertFalse(request.get().contains("email@example.com")); assertFalse(request.get().contains("123456789"));
            assertTrue(request.get().contains("max_tokens")); assertTrue(request.get().contains("1200"));
            assertThrows(IllegalStateException.class,()->gateway.extract("Account EUR 10",true)); assertEquals(1,calls.get());
        } finally { server.stop(0); }
    }
    @Test void disabledOrUnsafeEndpointCannotSendSource() throws Exception {
        var mapper = new ObjectMapper();
        assertFalse(new EvidenceModelGateway(mapper,true,"http://public.example/extract","model","key",5).configured());
        assertFalse(new EvidenceModelGateway(mapper,false,"https://example.com/extract","model","key",5).configured());
        assertFalse(new EvidenceModelGateway(mapper,true,"https://user:key@example.com/extract","model","key",5).configured());
    }
}
