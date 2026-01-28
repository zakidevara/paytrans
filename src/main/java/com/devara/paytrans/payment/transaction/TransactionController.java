package com.devara.paytrans.payment.transaction;

import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.reactor.ratelimiter.operator.RateLimiterOperator;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
@Slf4j
public class TransactionController {

  private final TransactionService service;
  private final RateLimiterRegistry rateLimiterRegistry;
  private final IdempotencyService idempotencyService;

  /**
   * Creates a new transaction with idempotency support.
   * Protected by a Rate Limiter to prevent system overload.
   * Uses Redis-based idempotency to prevent duplicate processing.
   * 
   * The idempotency key should be a unique identifier (e.g., UUID) that the client
   * generates and sends with the request. If the same key is used within 24 hours:
   * - Returns the cached result if the transaction completed
   * - Returns 409 Conflict if the transaction is still processing
   */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public Mono<Transaction> createTransaction(@Valid @RequestBody TransactionRequest request) {
    log.info("Received transaction request with idempotency key: {}, amount: {}", 
        request.getIdempotencyKey(), request.getAmount());

    // Wrap the payment processing in idempotency check
    Mono<Transaction> paymentOperation = service.processPayment(request.getAmount(), request.getCurrency())
        .transformDeferred(RateLimiterOperator.of(rateLimiterRegistry.rateLimiter("ingestionLimiter")))
        .onErrorResume(io.github.resilience4j.ratelimiter.RequestNotPermitted.class, ex -> {
          log.warn("Rate limit exceeded for currency: {}", request.getCurrency());
          return Mono.error(new ResponseStatusException(
              HttpStatus.TOO_MANY_REQUESTS,
              "System is currently busy. Please try again later."
          ));
        });

    // Execute with idempotency guarantee
    return idempotencyService.executeIdempotent(request.getIdempotencyKey(), paymentOperation)
        .onErrorResume(IdempotencyService.DuplicateRequestException.class, ex -> {
          log.warn("Duplicate request detected: {}", ex.getMessage());
          return Mono.error(new ResponseStatusException(
              HttpStatus.CONFLICT,
              ex.getMessage()
          ));
        });
  }

  /**
   * DTO for the request body.
   * Validates input before it reaches the service logic.
   */
  @Data
  public static class TransactionRequest {
    @NotBlank(message = "Idempotency key is required")
    private String idempotencyKey;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be positive")
    private BigDecimal amount;

    @NotNull(message = "Currency is required")
    private String currency;
  }
}
