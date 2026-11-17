package com.householdfinance.access;
import java.util.Set;
import org.junit.jupiter.api.extension.*;
/** Explicit trusted principal for older domain/controller contract fixtures, never runtime fallback. */
public class OwnerFixture implements BeforeEachCallback,AfterEachCallback {
  public static <T> T call(java.util.concurrent.Callable<T> action) throws Exception {
    var previous=AccessContext.actor();
    new OwnerFixture().beforeEach(null);
    try {return action.call();}
    finally {if(previous==null)AccessContext.clear();else AccessContext.enter(previous);}
  }
  public void beforeEach(ExtensionContext c){AccessContext.enter(new AccessContext.Actor("owner","owner",Set.of("ADMIN","EDITOR","READER"),0,true));}
  public void afterEach(ExtensionContext c){AccessContext.clear();}
}
