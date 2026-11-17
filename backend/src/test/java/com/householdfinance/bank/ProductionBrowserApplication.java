package com.householdfinance.bank;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.householdfinance.HouseholdFinanceApplication;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;

/** Production protocol simulation only on test classpath; no runtime URL overrides exist. */
public class ProductionBrowserApplication {
  public static void main(String[] args) { new SpringApplicationBuilder(HouseholdFinanceApplication.class,Fixture.class).run(args); }
  @TestConfiguration public static class Fixture {
    @Bean(destroyMethod="close") SyntheticProvider syntheticProvider() throws Exception {
      var p=new SyntheticProvider("http://127.0.0.1:8082/bank/callback"); p.environment="PRODUCTION"; return p;
    }
    @Bean @Primary SandboxGateway syntheticProductionGateway(SyntheticProvider p) throws Exception {
      Path folder=Files.createTempDirectory("synthetic-production-browser-");
      // Exercise the real store checks on Windows as well as Linux. This fixture
      // is test-only and must not weaken permissions to make the browser run.
      var acl=Files.getFileAttributeView(folder,AclFileAttributeView.class);
      if (acl!=null) {
        acl.setAcl(List.of(AclEntry.newBuilder().setType(AclEntryType.ALLOW)
            .setPrincipal(acl.getOwner()).setPermissions(EnumSet.allOf(AclEntryPermission.class))
            .setFlags(AclEntryFlag.FILE_INHERIT,AclEntryFlag.DIRECTORY_INHERIT).build()));
      } else {
        Files.setPosixFilePermissions(folder,PosixFilePermissions.fromString("rwx------"));
      }
      byte[] key=new byte[32]; new java.security.SecureRandom().nextBytes(key);
      var config=new ProductionConfig(true,p.app,"unused",p.callback,folder.resolve("connection.bin").toString(),Base64.getEncoder().encodeToString(key),true);
      return new SandboxGateway(config,p.base(),p.key::getPrivate,new ObjectMapper());
    }
  }
}
