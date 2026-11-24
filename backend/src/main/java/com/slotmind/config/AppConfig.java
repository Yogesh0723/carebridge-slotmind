package com.slotmind.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AppConfig implements WebMvcConfigurer {
  @Bean
  public SimpleAsyncTaskExecutor taskExecutor() {
    var executor = new SimpleAsyncTaskExecutor("slotmind-agent-");
    executor.setVirtualThreads(true);
    executor.setConcurrencyLimit(32);
    executor.setTaskTerminationTimeout(30_000L);
    return executor;
  }

  public void addCorsMappings(CorsRegistry r) {
    r.addMapping("/**")
        .allowedOrigins("http://localhost:5173")
        .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
  }
}
