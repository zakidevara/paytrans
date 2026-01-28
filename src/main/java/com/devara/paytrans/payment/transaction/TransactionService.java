package com.devara.paytrans.payment.transaction;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.StatusCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Random;

@Service
@Slf4j
public class TransactionService {

  private final TransactionRepository repository;
  private final Tracer tracer;
  private final KafkaSender<String, TransactionEvent> kafkaSender;
  private static final String TOPIC = "transaction-events";
  private final Random random = new Random();

  public TransactionService(TransactionRepository repository, OpenTelemetry openTelemetry,
                            KafkaSender<String, TransactionEvent> kafkaSender) {
    this.repository = repository;
    this.tracer = openTelemetry.getTracer("paytrans-service");
    this.kafkaSender = kafkaSender;
  }

  public Mono<Transaction> processPayment(BigDecimal amount, String currency) {
    log.info("Starting payment processing for amount: {} {}", amount, currency);

    // Step 1: Fraud Detection
    return detectFraud(amount, currency)
        // Step 2: Currency Conversion
        .flatMap(fraudScore -> convertCurrency(amount, currency))
        // Step 3: Calculate Fees
        .flatMap(convertedAmount -> calculateFees(convertedAmount))
        // Step 4: Save Transaction
        .flatMap(feeInfo -> saveTransaction(amount, currency, feeInfo))
        // Step 5: Send Notification
        .flatMap(this::sendNotification)
        // Step 6: Publish to Kafka
        .flatMap(this::publishEvent);
  }

  /**
   * Step 1: Fraud Detection
   * Simulates fraud detection service with random delay
   */
  private Mono<Double> detectFraud(BigDecimal amount, String currency) {
    Span span = tracer.spanBuilder("fraud-detection").startSpan();
    span.setAttribute("transaction.amount", amount.doubleValue());
    span.setAttribute("transaction.currency", currency);

    return Mono.fromCallable(() -> {
          // Simulate fraud detection processing time (50-150ms)
          Thread.sleep(50 + random.nextInt(100));

          // Calculate fraud score (0.0 - 1.0)
          double fraudScore = random.nextDouble() * 0.3; // Max 30% risk
          log.info("Fraud detection completed. Risk score: {}", fraudScore);

          span.setAttribute("fraud.score", fraudScore);
          span.setAttribute("fraud.status", fraudScore < 0.2 ? "low_risk" : "medium_risk");

          if (fraudScore > 0.8) {
            span.setStatus(StatusCode.ERROR, "High fraud risk detected");
            throw new RuntimeException("Transaction blocked: High fraud risk");
          }

          return fraudScore;
        })
        .doFinally(signal -> span.end());
  }

  /**
   * Step 2: Currency Conversion
   * Simulates currency conversion service
   */
  private Mono<BigDecimal> convertCurrency(BigDecimal amount, String currency) {
    Span span = tracer.spanBuilder("currency-conversion").startSpan();
    span.setAttribute("original.currency", currency);
    span.setAttribute("target.currency", "USD");

    return Mono.fromCallable(() -> {
          // Simulate external API call delay (30-100ms)
          Thread.sleep(30 + random.nextInt(70));

          // Mock exchange rates
          BigDecimal exchangeRate = switch (currency.toUpperCase()) {
            case "USD" -> BigDecimal.ONE;
            case "EUR" -> BigDecimal.valueOf(1.10);
            case "GBP" -> BigDecimal.valueOf(1.27);
            case "JPY" -> BigDecimal.valueOf(0.0091);
            case "IDR" -> BigDecimal.valueOf(0.000064);
            default -> BigDecimal.ONE;
          };

          BigDecimal convertedAmount = amount.multiply(exchangeRate)
              .setScale(2, RoundingMode.HALF_UP);

          log.info("Currency conversion: {} {} = {} USD (rate: {})",
              amount, currency, convertedAmount, exchangeRate);

          span.setAttribute("exchange.rate", exchangeRate.doubleValue());
          span.setAttribute("converted.amount", convertedAmount.doubleValue());

          return convertedAmount;
        })
        .doFinally(signal -> span.end());
  }

  /**
   * Step 3: Fee Calculation
   * Calculates processing fees based on amount
   */
  private Mono<FeeInfo> calculateFees(BigDecimal amount) {
    Span span = tracer.spanBuilder("calculate-fees").startSpan();
    span.setAttribute("transaction.amount", amount.doubleValue());

    return Mono.fromCallable(() -> {
          // Simulate fee calculation processing (20-60ms)
          Thread.sleep(20 + random.nextInt(40));

          // Calculate fees: 2.5% + $0.30
          BigDecimal percentageFee = amount.multiply(BigDecimal.valueOf(0.025));
          BigDecimal fixedFee = BigDecimal.valueOf(0.30);
          BigDecimal totalFee = percentageFee.add(fixedFee)
              .setScale(2, RoundingMode.HALF_UP);

          BigDecimal netAmount = amount.subtract(totalFee);

          log.info("Fee calculation: Amount={}, Fee={}, Net={}", amount, totalFee, netAmount);

          span.setAttribute("fee.percentage", percentageFee.doubleValue());
          span.setAttribute("fee.fixed", fixedFee.doubleValue());
          span.setAttribute("fee.total", totalFee.doubleValue());
          span.setAttribute("amount.net", netAmount.doubleValue());

          return new FeeInfo(amount, totalFee, netAmount);
        })
        .doFinally(signal -> span.end());
  }

  /**
   * Step 4: Save Transaction to Database
   */
  private Mono<Transaction> saveTransaction(BigDecimal amount, String currency, FeeInfo feeInfo) {
    Span span = tracer.spanBuilder("save-transaction").startSpan();
    span.setAttribute("transaction.amount", amount.doubleValue());
    span.setAttribute("transaction.currency", currency);

    Transaction tx = Transaction.builder()
        .amount(amount)
        .currency(currency)
        .status("COMPLETED")
        .createdAt(Instant.now())
        .build();

    return repository.save(tx)
        .doOnSuccess(savedTx -> {
          log.info("Transaction saved with ID: {}", savedTx.getId());
          span.setAttribute("transaction.id", savedTx.getId());
          span.setAttribute("transaction.status", savedTx.getStatus());
        })
        .doFinally(signal -> span.end());
  }

  /**
   * Step 5: Send Notification
   * Simulates sending notification to user
   */
  private Mono<Transaction> sendNotification(Transaction transaction) {
    Span span = tracer.spanBuilder("send-notification").startSpan();
    span.setAttribute("transaction.id", transaction.getId());
    span.setAttribute("notification.type", "email");

    return Mono.fromCallable(() -> {
          // Simulate notification service delay (40-120ms)
          Thread.sleep(40 + random.nextInt(80));

          log.info("Notification sent for transaction ID: {}", transaction.getId());

          span.setAttribute("notification.status", "sent");
          span.setAttribute("notification.channel", "email");

          return transaction;
        })
        .doFinally(signal -> span.end());
  }

  /**
   * Step 6: Publish Event to Kafka
   */
  private Mono<Transaction> publishEvent(Transaction transaction) {
    Span span = tracer.spanBuilder("publish-kafka-event").startSpan();
    span.setAttribute("transaction.id", transaction.getId());
    span.setAttribute("kafka.topic", TOPIC);

    TransactionEvent event = new TransactionEvent(
        transaction.getId(),
        transaction.getAmount(),
        transaction.getStatus()
    );

    ProducerRecord<String, TransactionEvent> record = new ProducerRecord<>(TOPIC, event);

    return kafkaSender.send(Mono.just(SenderRecord.create(record, transaction.getId())))
        .doOnNext(result -> {
          log.info("Published transaction event to Kafka for ID: {}", transaction.getId());
          span.setAttribute("kafka.partition", result.recordMetadata().partition());
          span.setAttribute("kafka.offset", result.recordMetadata().offset());
        })
        .doOnError(error -> {
          log.error("Failed to publish to Kafka", error);
          span.setStatus(StatusCode.ERROR, "Kafka publish failed");
        })
        .then(Mono.just(transaction))
        .doFinally(signal -> span.end());
  }

  /**
   * Helper class to hold fee calculation results
   */
  private record FeeInfo(BigDecimal grossAmount, BigDecimal fee, BigDecimal netAmount) {}
}
