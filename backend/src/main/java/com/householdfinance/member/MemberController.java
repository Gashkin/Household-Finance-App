package com.householdfinance.member;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.cfg.*;
import com.fasterxml.jackson.databind.type.LogicalType;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/member")
public class MemberController {
  private final MemberService service;
  private final ObjectMapper json;
  public MemberController(MemberService service,ObjectMapper mapper){
    this.service=service;json=mapper.copy().disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
      .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);
    json.enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    json.coercionConfigFor(LogicalType.Integer).setCoercion(CoercionInputShape.String,CoercionAction.Fail);
    json.coercionConfigFor(LogicalType.Boolean).setCoercion(CoercionInputShape.String,CoercionAction.Fail).setCoercion(CoercionInputShape.Integer,CoercionAction.Fail);
    json.coercionConfigFor(LogicalType.Textual).setCoercion(CoercionInputShape.Integer,CoercionAction.Fail).setCoercion(CoercionInputShape.Float,CoercionAction.Fail).setCoercion(CoercionInputShape.Boolean,CoercionAction.Fail);
  }
  private <T>T body(HttpServletRequest r,Class<T> type)throws IOException{
    checkSize(r.getContentLengthLong());byte[] bytes=r.getInputStream().readNBytes(16_385);
    try{checkSize(bytes.length);T value=json.readValue(bytes,type);if(value==null)throw new IllegalArgumentException("Complete member request required");return value;}
    catch(IOException e){throw new IllegalArgumentException("Invalid member fields or types");}
    finally{Arrays.fill(bytes,(byte)0);}
  }
  private void checkSize(long length){if(length>16_384)throw new IllegalArgumentException("Member request too large");}
  @GetMapping public MemberService.Workspace get(){return service.get();}
  @GetMapping("/report") public MemberService.Report report(){return service.report();}
  @GetMapping("/export") public ResponseEntity<MemberService.Report> export(){return ResponseEntity.ok().header("Content-Disposition","attachment; filename=permitted-spending-report.json").body(service.report());}
  @PostMapping("/publication") public MemberService.Workspace publish(HttpServletRequest r)throws IOException{return service.publish(body(r,MemberService.Publication.class));}
  @PostMapping("/purchases") public MemberService.Workspace create(HttpServletRequest r)throws IOException{return service.create(body(r,MemberService.NewPurchase.class));}
  @PutMapping("/purchases/{id}") public MemberService.Workspace change(@PathVariable("id")String id,HttpServletRequest r)throws IOException{return service.change(id,body(r,MemberService.Change.class));}
  @GetMapping("/purchases/{id}/history") public List<MemberService.Event> history(@PathVariable("id")String id){return service.history(id);}
  @PutMapping("/preferences") public MemberService.Workspace preferences(HttpServletRequest r)throws IOException{return service.preferences(body(r,MemberService.PreferenceInput.class));}
  @ExceptionHandler({IllegalArgumentException.class,ArithmeticException.class,java.time.DateTimeException.class})
  public ResponseEntity<Map<String,String>> invalid(Exception e){return ResponseEntity.badRequest().body(Map.of("error",e instanceof java.time.DateTimeException?"Use a valid YYYY-MM-DD date":Objects.toString(e.getMessage(),"Invalid member request")));}
}
