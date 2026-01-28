package com.devara.paytrans.payment.transaction; // Updated Package

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import java.time.Instant;
import java.math.BigDecimal;

@Service
@Slf4j
public class TransactionService {

  private final TransactionRepository repository;
  private final Tracer tracer;

  private final KafkaTemplate<String, TransactionEvent> kafkaTemplate;
  private static final String TOPIC = "transaction-events";

  // Manual constructor for injections
  public TransactionService(TransactionRepository repository, OpenTelemetry openTelemetry, KafkaTemplate<String, TransactionEvent> kafkaTemplate) {
    this.repository = repository;
    this.tracer = openTelemetry.getTracer("paytrans-service");
    this.kafkaTemplate = kafkaTemplate;
  }

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
          return repository.save(tx).doOnSuccess(
              savedTx -> {
                log.info("Transaction saved with ID: {}", savedTx.getId());
                TransactionEvent event = new TransactionEvent(
                    savedTx.getId(),
                    savedTx.getAmount(),
                    savedTx.getStatus()
                );
                kafkaTemplate.send(TOPIC, event);
                log.info("Published transaction event to Kafka for ID: {}", savedTx.getId());
              }
          );
        });
  }
}