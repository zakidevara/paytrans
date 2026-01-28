package com.devara.paytrans.payment.transaction;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;

import java.math.BigDecimal;
import java.time.Instant;

@Service
@Slf4j
public class TransactionService {

  private final TransactionRepository repository;
  private final Tracer tracer;
  private final KafkaSender<String, TransactionEvent> kafkaSender;
  private static final String TOPIC = "transaction-events";

  public TransactionService(TransactionRepository repository, OpenTelemetry openTelemetry,
                            KafkaSender<String, TransactionEvent> kafkaSender) {
    this.repository = repository;
    this.tracer = openTelemetry.getTracer("paytrans-service");
    this.kafkaSender = kafkaSender;
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

          return repository.save(tx).flatMap(savedTx -> {
            log.info("Transaction saved with ID: {}", savedTx.getId());
            TransactionEvent event = new TransactionEvent(
                savedTx.getId(),
                savedTx.getAmount(),
                savedTx.getStatus()
            );

            // Reactive Kafka send
            ProducerRecord<String, TransactionEvent> record = new ProducerRecord<>(TOPIC, event);
            return kafkaSender.send(Mono.just(SenderRecord.create(record, savedTx.getId())))
                .doOnNext(result -> log.info("Published transaction event to Kafka for ID: {}", savedTx.getId()))
                .then(Mono.just(savedTx));
          });
        });
  }
}
