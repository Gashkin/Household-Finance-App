package com.householdfinance.access;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/** Small product-defined catalogue, not a configurable policy engine. */
@Service
public class AccessService {
  public static final Set<String> ROLES = Set.of("ADMIN","EDITOR","READER");
  public record User(String id,String username,Set<String> roles,String state,long revision,boolean custodian,boolean accepted) {}
  public record Invitation(String username,String code,Instant expiresAt) {}
  public record Change(long revision,Set<String> roles,String state) {}
  public record Outcome(boolean accepted,String message) {}
  private final JdbcTemplate jdbc;
  private final String bootstrap;
  private final SecureRandom random = new SecureRandom();
  @org.springframework.beans.factory.annotation.Autowired(required=false) Clock clock=Clock.systemUTC();
  private Instant attemptWindow = Instant.EPOCH;
  private int publicAttempts;
  public AccessService(JdbcTemplate jdbc,@Value("${workspace.password:}") String bootstrap) { this.jdbc=jdbc;this.bootstrap=bootstrap; }
  public boolean configured() { return bootstrap.length()>=16 && bootstrap.length()<=256 || jdbc.queryForObject("SELECT COUNT(*) FROM HOUSEHOLD_USERS WHERE ID='owner'",Long.class)>0; }
  private String secret() { byte[] b=new byte[32];random.nextBytes(b);return Base64.getUrlEncoder().withoutPadding().encodeToString(b); }
  private static String hash(String text) {
    try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
    catch(GeneralSecurityException e){throw new IllegalStateException("Cryptography unavailable",e);}
  }
  private String credential(String password) {
    if(password==null||password.length()<16||password.length()>256)throw new IllegalArgumentException("Use a password of 16–256 characters");
    byte[] salt=new byte[16];random.nextBytes(salt);
    return Base64.getEncoder().encodeToString(salt)+":"+derive(password,salt);
  }
  private static String derive(String password,byte[] salt) {
    var spec=new PBEKeySpec(password.toCharArray(),salt,600_000,256);
    try { return Base64.getEncoder().encodeToString(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded()); }
    catch(GeneralSecurityException e){throw new IllegalStateException("Cryptography unavailable",e);}
    finally {spec.clearPassword();}
  }
  public boolean verifies(String id,String password) {
    if(password==null||password.length()>256)return false;
    var rows=jdbc.queryForList("SELECT CREDENTIAL FROM HOUSEHOLD_USERS WHERE ID=? AND STATE='ACTIVE'",id);
    if(rows.isEmpty()||rows.get(0).get("CREDENTIAL")==null)return false;
    try {
      String[] parts=rows.get(0).get("CREDENTIAL").toString().split(":");
      return MessageDigest.isEqual(derive(password,Base64.getDecoder().decode(parts[0])).getBytes(StandardCharsets.US_ASCII),parts[1].getBytes(StandardCharsets.US_ASCII));
    }catch(IllegalArgumentException e){return false;}
  }
  @Transactional
  public synchronized void ensureOwner() {
    if(bootstrap.length()<16||bootstrap.length()>256)return;
    if(jdbc.queryForObject("SELECT COUNT(*) FROM HOUSEHOLD_USERS",Long.class)==0L) {
      jdbc.update("INSERT INTO HOUSEHOLD_USERS(ID,USERNAME,CREDENTIAL,ROLES,STATE,REVISION) VALUES('owner','owner',?,'ADMIN,EDITOR,READER','ACTIVE',0)",credential(bootstrap));
    }
  }
  private Set<String> roles(String text) { return text==null||text.isBlank()?Set.of():Set.copyOf(Arrays.asList(text.split(","))); }
  public User user(String id) {
    var list=jdbc.query("SELECT ID,USERNAME,ROLES,STATE,REVISION,CREDENTIAL FROM HOUSEHOLD_USERS WHERE ID=?",(r,n)->new User(r.getString(1),r.getString(2),roles(r.getString(3)),r.getString(4),r.getLong(5),r.getString(1).equals("owner"),r.getString(6)!=null),id);
    return list.isEmpty()?null:list.get(0);
  }
  @Transactional
  public AccessContext.Actor authenticate(String username,String password) {
    ensureOwner();
    if(username==null)username="owner";
    var list=jdbc.queryForList("SELECT ID FROM HOUSEHOLD_USERS WHERE USERNAME=?",username.strip().toLowerCase(Locale.ROOT));
    String id=list.isEmpty()?"missing":list.get(0).get("ID").toString();
    boolean valid=verifies(id,password);
    var u=user(id);
    // Unknown, unaccepted and disabled accounts also perform password derivation work.
    if((u==null||!u.state().equals("ACTIVE")||!u.accepted())&&password!=null&&password.length()<=256)derive(password,new byte[16]);
    if(!valid||u==null||!u.state().equals("ACTIVE"))return null;
    audit(id,id,"SIGN_IN","SUCCESS");
    return actor(u);
  }
  public AccessContext.Actor active(String id,long revision) {
    var u=user(id);
    return u==null||!u.state().equals("ACTIVE")||u.revision()!=revision?null:actor(u);
  }
  private AccessContext.Actor actor(User u){return new AccessContext.Actor(u.id(),u.username(),u.roles(),u.revision(),u.custodian());}
  public static void forbidden(){throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Capability not granted");}
  private void admin(){if(AccessContext.actor()==null||!AccessContext.actor().roles().contains("ADMIN"))forbidden();}
  private void ownerLock(){jdbc.queryForList("SELECT ID FROM HOUSEHOLD_USERS WHERE ID='owner' FOR UPDATE");}
  public List<User> users(){admin();return jdbc.query("SELECT ID FROM HOUSEHOLD_USERS ORDER BY USERNAME",(r,n)->user(r.getString(1)));}
  private static String username(String text){
    if(text==null||!text.strip().matches("[A-Za-z][A-Za-z0-9_.-]{2,39}"))throw new IllegalArgumentException("Use a unique username of 3–40 letters, digits, dots, hyphens or underscores");
    return text.strip().toLowerCase(Locale.ROOT);
  }
  @Transactional
  public Invitation invite(String name){
    admin();ownerLock();String login=username(name);
    if(jdbc.queryForObject("SELECT COUNT(*) FROM HOUSEHOLD_USERS",Long.class)>=HouseholdLimits.MAX_USERS)throw new IllegalArgumentException("Household user limit reached");
    if(jdbc.queryForObject("SELECT COUNT(*) FROM HOUSEHOLD_USERS WHERE USERNAME=?",Long.class,login)!=0)throw new IllegalArgumentException("Choose another username");
    String id=UUID.randomUUID().toString(),code=secret();Instant expiry=Instant.now(clock).plus(Duration.ofDays(7));
    jdbc.update("INSERT INTO HOUSEHOLD_USERS(ID,USERNAME,ROLES,STATE,REVISION,INVITATION_HASH,INVITATION_EXPIRY) VALUES(?,?,'','INVITED',0,?,?)",id,login,hash(code),java.sql.Timestamp.from(expiry));
    audit(AccessContext.userId(),id,"INVITE","SUCCESS");return new Invitation(login,code,expiry);
  }
  private synchronized void publicLimit(){Instant now=Instant.now(clock);if(!now.isBefore(attemptWindow.plusSeconds(60))){attemptWindow=now;publicAttempts=0;}if(++publicAttempts>10)throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Try again later");}
  @Transactional
  public Outcome accept(String code,String password,boolean accepted){
    publicLimit();ownerLock();
    if(!accepted||code==null||code.length()>100)return new Outcome(false,"Invitation could not be accepted");
    var list=jdbc.queryForList("SELECT ID FROM HOUSEHOLD_USERS WHERE INVITATION_HASH=? AND INVITATION_EXPIRY>? AND STATE='INVITED'",hash(code),java.sql.Timestamp.from(Instant.now(clock)));
    if(list.size()!=1)return new Outcome(false,"Invitation could not be accepted");
    String id=list.get(0).get("ID").toString();
    jdbc.update("UPDATE HOUSEHOLD_USERS SET CREDENTIAL=?,STATE='ACTIVE',INVITATION_HASH=NULL,INVITATION_EXPIRY=NULL,REVISION=REVISION+1 WHERE ID=?",credential(password),id);
    audit(id,id,"ACCEPT","SUCCESS");return new Outcome(true,"Account ready; sign in with your username. An administrator can now grant roles.");
  }
  @Transactional
  public Invitation reissue(String id,boolean revoke){
    admin();ownerLock();var u=user(id);
    if(u==null||u.accepted())throw new IllegalArgumentException("An account awaiting invitation acceptance is required");
    String code=secret();Instant expiry=Instant.now(clock).plus(Duration.ofDays(7));
    jdbc.update("UPDATE HOUSEHOLD_USERS SET INVITATION_HASH=?,INVITATION_EXPIRY=?,STATE=?,REVISION=REVISION+1 WHERE ID=?",revoke?null:hash(code),revoke?null:java.sql.Timestamp.from(expiry),revoke?"DISABLED":"INVITED",id);
    audit(AccessContext.userId(),id,revoke?"INVITE_REVOKE":"INVITE_REISSUE","SUCCESS");
    return new Invitation(u.username(),revoke?"":code,expiry);
  }
  @Transactional
  public Outcome change(String id,Change change){
    admin();ownerLock();var u=user(id);
    if(u==null||change==null||change.roles()==null||!change.roles().stream().allMatch(r -> r!=null&&ROLES.contains(r))||!Set.of("ACTIVE","DISABLED").contains(Objects.toString(change.state(),"")))throw new IllegalArgumentException("Invalid user change");
    if(u.revision()!=change.revision())return new Outcome(false,"Account changed; reload before saving");
    if(!u.accepted())return new Outcome(false,"The person must accept their invitation first");
    if(id.equals(AccessContext.userId())){audit(AccessContext.userId(),id,"USER_CHANGE","REJECTED_SELF");return new Outcome(false,"Another administrator must change your roles or disable your account");}
    long admins=jdbc.queryForObject("SELECT COUNT(*) FROM HOUSEHOLD_USERS WHERE STATE='ACTIVE' AND (',' || ROLES || ',') LIKE '%,ADMIN,%'",Long.class);
    if(u.state().equals("ACTIVE")&&u.roles().contains("ADMIN")&&(!change.roles().contains("ADMIN")||!change.state().equals("ACTIVE"))&&admins<=1){audit(AccessContext.userId(),id,"USER_CHANGE","REJECTED_LAST_ADMIN");return new Outcome(false,"Keep at least one active administrator");}
    jdbc.update("UPDATE HOUSEHOLD_USERS SET ROLES=?,STATE=?,REVISION=REVISION+1 WHERE ID=?",String.join(",",new TreeSet<>(change.roles())),change.state(),id);
    audit(AccessContext.userId(),id,"USER_CHANGE","SUCCESS");return new Outcome(true,"Account updated; existing sessions were revoked");
  }
  @Transactional
  public String recoveryCode(){String code=secret();jdbc.update("UPDATE HOUSEHOLD_USERS SET RECOVERY_HASH=? WHERE ID=?",hash(code),AccessContext.userId());audit(AccessContext.userId(),AccessContext.userId(),"RECOVERY_ROTATE","SUCCESS");return code;}
  @Transactional
  public Outcome recover(String name,String code,String password){
    publicLimit();ownerLock();if(name==null||code==null||code.length()>100)return new Outcome(false,"Recovery could not be completed");
    var list=jdbc.queryForList("SELECT ID FROM HOUSEHOLD_USERS WHERE USERNAME=? AND RECOVERY_HASH=? AND STATE='ACTIVE'",name.toLowerCase(Locale.ROOT),hash(code));
    if(list.size()!=1)return new Outcome(false,"Recovery could not be completed");
    String id=list.get(0).get("ID").toString();jdbc.update("UPDATE HOUSEHOLD_USERS SET CREDENTIAL=?,RECOVERY_HASH=NULL,REVISION=REVISION+1 WHERE ID=?",credential(password),id);
    audit(id,id,"RECOVER","SUCCESS");return new Outcome(true,"Password changed; previous sessions and recovery code revoked");
  }
  @Transactional
  public void audit(String actor,String target,String action,String outcome){
    jdbc.update("INSERT INTO ACCESS_AUDIT(ID,ACTOR_ID,TARGET_ID,ACTION,OUTCOME,RECORDED_AT) VALUES(?,?,?,?,?,?)",UUID.randomUUID().toString(),actor,target,action,outcome,java.sql.Timestamp.from(Instant.now(clock)));
    jdbc.update("DELETE FROM ACCESS_AUDIT WHERE ID IN (SELECT ID FROM ACCESS_AUDIT ORDER BY RECORDED_AT DESC,ID DESC OFFSET 5000 ROWS)");
  }
  public List<Map<String,Object>> audit(){admin();return jdbc.queryForList("SELECT ACTOR_ID,TARGET_ID,ACTION,OUTCOME,RECORDED_AT FROM ACCESS_AUDIT ORDER BY RECORDED_AT DESC LIMIT 200");}
  public void scopeChanged(){jdbc.update("UPDATE HOUSEHOLD_USERS SET REVISION=REVISION+1 WHERE ID<>? AND STATE='ACTIVE'",AccessContext.userId());}
  public boolean permits(String path,String method,AccessContext.Actor a){
    if(path.equals("/api/access/me")||path.equals("/api/access/reauth")||path.equals("/api/access/recovery-code")||path.equals("/api/workspace/lock")||path.equals("/api/workspace/status")||path.equals("/auth/me"))return true;
    if(path.startsWith("/api/access/"))return a.roles().contains("ADMIN");
    if(path.startsWith("/api/workspace/"))return a.custodian();
    boolean get=method.equals("GET")||method.equals("HEAD");
    // Members may maintain personal decisions and submit requests without ledger edit rights.
    // Domain methods enforce ownership, publication and receiving-editor decisions.
    if(path.equals("/api/member")||path.startsWith("/api/member/"))return a.reads();
    if(path.startsWith("/api/ledger")||path.startsWith("/api/cash-position")||path.startsWith("/api/forecast")||path.startsWith("/api/plans")||path.startsWith("/api/debt-scenarios")){
      boolean calculation=Set.of("/api/debt-scenarios/compare","/api/debt-scenarios/report","/api/forecast/compare").contains(path);
      return (get||calculation)?a.reads():a.edits();
    }
    // Legacy records, temporary intake and bank connections predate named ownership.
    // Only the original financial owner may reach these surfaces; Admin is insufficient.
    if(path.startsWith("/planner")||path.startsWith("/imports")||path.startsWith("/migration")||path.startsWith("/reports/")||path.startsWith("/api/evidence-intake")||path.startsWith("/api/bank"))return a.custodian()&&a.edits();
    return false;
  }
}
