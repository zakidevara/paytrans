package com.devara.paytrans.payment.transaction; // Updated Package

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import java.time.Instant;
import java.math.BigDecimal;

@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionService {

  private final TransactionRepository repository;
  private final Tracer tracer;

  public Mono<Transaction> processPayment(BigDecimal amount, String currency) {
    Span span = tracer.spanBuilder("calculate_fees").startSpan();

    return Mono.fromCallable(() -> {
          log.info("Calculating fees for amount: {}", amount);
          return "COMPLETED";
        })
        .doFinally(signal -> span.end())
        .flatMap(status -> {
          Transaction tx = Transaction.builder()
              .amount(amount)
              .currency(currency)
              .status(status)
              .createdAt(Instant.now())
              .build();
          return repository.save(tx);
        });
  }
}