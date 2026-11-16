package com.householdfinance.access;
import java.util.Set;
import org.junit.jupiter.api.extension.*;
/** Explicit trusted principal for older domain/controller contract fixtures, never runtime fallback. */
public class OwnerFixture implements BeforeEachCallback,AfterEachCallback {
  public void beforeEach(ExtensionContext c){AccessContext.enter(new AccessContext.Actor("owner","owner",Set.of("ADMIN","EDITOR","READER"),0,true));}
  public void afterEach(ExtensionContext c){AccessContext.clear();}
}
