package com.householdfinance.bank;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Explicit local deployment configuration; disabled in source and CI by default. */
@Component
public record ProductionConfig(
    @Value("${bank.production.enabled:false}") boolean enabled,
    @Value("${bank.production.app-id:}") String appId,
    @Value("${bank.production.key-path:}") String keyPath,
    @Value("${bank.production.callback:http://127.0.0.1:8080/bank/callback}") String callback,
    @Value("${bank.production.store-path:}") String storePath,
    @Value("${bank.production.storage-key:}") String storageKey,
    @Value("${bank.production.readiness:false}") boolean readiness) {
  @Override public String toString() { return "ProductionConfig[enabled=" + enabled + "]"; }
  static ProductionConfig disabled() {
    return new ProductionConfig(false, "", "", "http://127.0.0.1:8080/bank/callback", "", "", false);
  }
}
