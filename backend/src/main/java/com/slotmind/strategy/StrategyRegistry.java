package com.slotmind.strategy;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.micrometer.core.instrument.MeterRegistry;

@Configuration
public class StrategyRegistry {
  @Bean
  public SchedulingStrategy schedulingStrategy(
      @Value("${slotmind.strategy:RULE_BASED}") String selected,
      @Value("${slotmind.ai.url}") String url,
      @Value("${slotmind.ai.token:}") String token,
      @Value("${slotmind.ai.model}") String model,
      @Value("${slotmind.ai.product:PC1}") String product,
      MeterRegistry meterRegistry) {
    return "ROOM_CONSTRAINT".equalsIgnoreCase(selected)
        ? new RoomConstraintStrategy()
        : "AI".equalsIgnoreCase(selected)
        ? new AiSchedulingStrategy(url, token, model, product,
            java.net.http.HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(10)).build(), meterRegistry)
        : new RuleBasedSchedulingStrategy();
  }
}
