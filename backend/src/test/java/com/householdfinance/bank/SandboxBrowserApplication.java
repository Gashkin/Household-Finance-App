package com.householdfinance.bank;

import com.householdfinance.HouseholdFinanceApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;

/** Synthetic browser transport exists only on the test classpath. */
public class SandboxBrowserApplication {
  public static void main(String[] args) {
    new SpringApplicationBuilder(HouseholdFinanceApplication.class, Fixture.class).run(args);
  }

  @TestConfiguration
  public static class Fixture {
    @Bean(destroyMethod = "close")
    SyntheticProvider syntheticProvider() throws Exception {
      return new SyntheticProvider("http://127.0.0.1:4173/bank/callback");
    }

    @Bean
    @Primary
    SandboxGateway syntheticGateway(SyntheticProvider provider) {
      return provider.gateway();
    }
  }
}
