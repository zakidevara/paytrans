package com.devara.paytrans.config;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenTelemetryConfig {

  @Bean
  public OpenTelemetry openTelemetry() {
    // This manually starts the OTel SDK without using the buggy Spring Starter
    return AutoConfiguredOpenTelemetrySdk.initialize().getOpenTelemetrySdk();
  }

  @Bean
  public Tracer tracer(OpenTelemetry openTelemetry) {
    // We name the tracer after our service so we know who created the spans
    return openTelemetry.getTracer("paytrans-service", "1.0.0");
  }

}