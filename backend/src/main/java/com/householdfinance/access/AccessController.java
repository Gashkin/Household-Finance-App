package com.householdfinance.access;

import com.fasterxml.jackson.databind.*;
import com.householdfinance.workspace.WorkspaceFilter;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/access")
public class AccessController {
  private final AccessService access; private final WorkspaceFilter sessions; private final ObjectMapper json;
  public AccessController(AccessService access,WorkspaceFilter sessions,ObjectMapper json){
    this.access=access;this.sessions=sessions;this.json=json.copy().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES).disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);
    for(var input:List.of(com.fasterxml.jackson.databind.cfg.CoercionInputShape.String,com.fasterxml.jackson.databind.cfg.CoercionInputShape.Integer,com.fasterxml.jackson.databind.cfg.CoercionInputShape.Float))this.json.coercionConfigFor(com.fasterxml.jackson.databind.type.LogicalType.Boolean).setCoercion(input,com.fasterxml.jackson.databind.cfg.CoercionAction.Fail);
  }
  record Invite(String username) {}
  record Accept(String code,String password,boolean accepted) {}
  record Recovery(String username,String code,String password) {}
  record Password(String password) {}
  private <T>T body(HttpServletRequest r,Class<T> type)throws IOException{
    byte[] bytes=r.getInputStream().readNBytes(8193);
    try {if(bytes.length>8192)throw new IllegalArgumentException("Access request too large");var v=json.readValue(bytes,type);if(v==null)throw new IllegalArgumentException("Complete access request required");return v;}
    catch(IOException e){throw new IllegalArgumentException("Invalid access request");}finally{Arrays.fill(bytes,(byte)0);}
  }
  @GetMapping("/me") public Map<String,Object> me(){var a=AccessContext.actor();return Map.of("id",a.id(),"username",a.username(),"roles",a.roles(),"custodian",a.custodian(),"canRead",a.reads(),"canEdit",a.edits(),"scope","LIMITED_OR_UNCERTAIN");}
  @GetMapping("/users") public List<AccessService.User> users(){return access.users();}
  @GetMapping("/audit") public List<Map<String,Object>> audit(){return access.audit();}
  @PostMapping("/invitations") public AccessService.Invitation invite(HttpServletRequest r)throws IOException{return access.invite(body(r,Invite.class).username());}
  @PostMapping("/invitations/{id}/reissue") public AccessService.Invitation reissue(@PathVariable("id")String id){return access.reissue(id,false);}
  @PostMapping("/invitations/{id}/revoke") public AccessService.Invitation revoke(@PathVariable("id")String id){return access.reissue(id,true);}
  @PutMapping("/users/{id}") public ResponseEntity<AccessService.Outcome> change(@PathVariable("id") String id,HttpServletRequest r)throws IOException{var result=access.change(id,body(r,AccessService.Change.class));return ResponseEntity.status(result.accepted()?200:409).body(result);}
  @PostMapping("/accept") public ResponseEntity<AccessService.Outcome> accept(HttpServletRequest r)throws IOException{var i=body(r,Accept.class);var o=access.accept(i.code(),i.password(),i.accepted());return ResponseEntity.status(o.accepted()?200:400).body(o);}
  @PostMapping("/recover") public ResponseEntity<AccessService.Outcome> recover(HttpServletRequest r)throws IOException{var i=body(r,Recovery.class);var o=access.recover(i.username(),i.code(),i.password());return ResponseEntity.status(o.accepted()?200:400).body(o);}
  @PostMapping("/recovery-code") public Map<String,String> recovery(){return Map.of("code",access.recoveryCode());}
  @PostMapping("/reauth") public ResponseEntity<Void> reauth(HttpServletRequest r)throws IOException{return ResponseEntity.status(sessions.reauthenticate(r,body(r,Password.class).password())?204:401).build();}
  @ExceptionHandler({IllegalArgumentException.class,org.springframework.dao.DataAccessException.class}) public ResponseEntity<Map<String,String>> invalid(Exception e){return ResponseEntity.badRequest().body(Map.of("error",e instanceof IllegalArgumentException?Objects.toString(e.getMessage(),"Invalid access request"):"Access change could not be completed"));}
}
