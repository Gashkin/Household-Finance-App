package com.householdfinance.access;

import java.util.Set;

/** Request identity, established only by the authenticated loopback filter. */
public final class AccessContext {
  public record Actor(String id, String username, Set<String> roles, long revision, boolean custodian) {
    public boolean reads() { return roles.contains("READER") || roles.contains("EDITOR"); }
    public boolean edits() { return roles.contains("EDITOR"); }
  }
  private static final ThreadLocal<Actor> CURRENT = new ThreadLocal<>();
  private AccessContext() {}
  public static void enter(Actor actor) { CURRENT.set(actor); }
  public static void clear() { CURRENT.remove(); }
  public static Actor actor() { return CURRENT.get(); }
  public static String userId() { if(actor()==null)throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED,"Authenticated identity required");return actor().id(); }
  public static void requireRead(){userId();if(!actor().reads())AccessService.forbidden();}
  public static void requireEdit(){userId();if(!actor().edits())AccessService.forbidden();}
  public static void requireCustodian(){userId();if(!actor().custodian())AccessService.forbidden();}
  public static void requireLegacy(){requireCustodian();requireEdit();}
  public static boolean owns(String owner) { return userId().equals(owner == null ? "owner" : owner); }
  public static boolean reads(String owner, boolean shared) { requireRead();return owns(owner) || shared; }
  public static String forecastId() { return userId().equals("owner") ? "household" : userId(); }
  public static String publicationId() { return userId().equals("owner") ? "HOUSEHOLD" : userId(); }
}
