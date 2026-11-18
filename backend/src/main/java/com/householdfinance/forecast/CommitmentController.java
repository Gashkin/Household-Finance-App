package com.householdfinance.forecast;

import com.householdfinance.ledger.CommitmentService;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.cfg.*;
import com.fasterxml.jackson.databind.type.LogicalType;
import jakarta.servlet.http.HttpServletRequest;
import java.io.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/forecast/commitments")
public class CommitmentController {
  private final CommitmentService service;
  private final ObjectMapper json;
  public CommitmentController(CommitmentService s,ObjectMapper mapper){
    service=s;json=mapper.copy().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES).disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);
    json.enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    for(var t:List.of(LogicalType.Integer,LogicalType.Boolean,LogicalType.Textual))for(var shape:List.of(CoercionInputShape.String,CoercionInputShape.Integer,CoercionInputShape.Float,CoercionInputShape.Boolean)){
      if(t==LogicalType.Textual&&shape==CoercionInputShape.String||t==LogicalType.Integer&&shape==CoercionInputShape.Integer||t==LogicalType.Boolean&&shape==CoercionInputShape.Boolean)continue;
      json.coercionConfigFor(t).setCoercion(shape,CoercionAction.Fail);
    }
  }
  private <T>T body(HttpServletRequest request,Class<T> type)throws IOException{
    byte[] data=request.getInputStream().readNBytes(262145);
    try{if(data.length>262144)throw new IllegalArgumentException("Commitment request too large");T value=json.readValue(data,type);if(value==null)throw new IllegalArgumentException("Complete request required");return value;}
    catch(IOException e){throw new IllegalArgumentException("Invalid commitment fields or types");}finally{Arrays.fill(data,(byte)0);}
  }
  @GetMapping public Object get(){return service.get();}
  @PostMapping("/batch") public Object batch(HttpServletRequest r)throws IOException{return service.batch(body(r,CommitmentService.Batch.class));}
  @PostMapping("/preview") public Object previewBatch(HttpServletRequest r)throws IOException{return service.previewBatch(body(r,CommitmentService.Batch.class));}
  @PostMapping("/payroll/preview") public Object previewPayroll(HttpServletRequest r)throws IOException{return service.previewPayroll(body(r,CommitmentService.PayrollEdit.class));}
  @PostMapping("/payroll") public Object payroll(HttpServletRequest r)throws IOException{return service.payroll(body(r,CommitmentService.PayrollEdit.class));}
  @PostMapping("/occurrence") public Object occurrence(HttpServletRequest r)throws IOException{return service.exception(body(r,CommitmentService.ExceptionInput.class));}
  @GetMapping("/upcoming") public Object upcoming(@RequestParam("from")String from,@RequestParam("to")String to){return service.upcoming(LocalDate.parse(from),LocalDate.parse(to));}
  @GetMapping("/candidates") public Object candidates(@RequestParam("id")String id){return service.candidates(id);}
  @GetMapping("/outlook") public Object outlook(@RequestParam("baseline")String baseline,@RequestParam("end")String end,@RequestParam(value="uncertain",defaultValue="false")boolean uncertain,@RequestParam(value="cashOnly",defaultValue="false")boolean cashOnly){return service.outlook(LocalDate.parse(baseline),LocalDate.parse(end),uncertain,cashOnly);}
  @GetMapping("/operations/{id}") public Object operation(@PathVariable("id")String id){return service.operation(id);}
  @GetMapping("/history") public Object history(){return service.history();}
  @PostMapping("/outlook/compare") public Object compare(HttpServletRequest r)throws IOException{return service.compare(body(r,CommitmentService.ComparisonInput.class));}
  @GetMapping(value="/csv",produces="text/csv") public ResponseEntity<String> csv(){return ResponseEntity.ok().header("Content-Disposition","attachment; filename=\"bill-updates.csv\"").body(service.csv());}
  @PostMapping("/csv/preview") public Object preview(HttpServletRequest r)throws IOException{return service.preview(body(r,CommitmentService.CsvInput.class));}
  @PostMapping("/csv/apply") public Object apply(HttpServletRequest r)throws IOException{return service.apply(body(r,CommitmentService.CsvApply.class));}
  @ExceptionHandler({IllegalArgumentException.class,java.time.DateTimeException.class,ArithmeticException.class})
  public ResponseEntity<Map<String,String>> invalid(Exception e){return ResponseEntity.badRequest().body(Map.of("error",e instanceof java.time.DateTimeException?"Invalid date":Objects.toString(e.getMessage(),"Invalid commitments")));}
}
