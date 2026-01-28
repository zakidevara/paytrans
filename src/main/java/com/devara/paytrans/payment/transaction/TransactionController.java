package com.devara.paytrans.payment.transaction;

import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.reactor.ratelimiter.operator.RateLimiterOperator;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
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

  /**
   * Creates a new transaction.
   * Protected by a Rate Limiter to prevent system overload.
   */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public Mono<Transaction> createTransaction(@Valid @RequestBody TransactionRequest request) {
    log.info("Received transaction request for amount: {}", request.getAmount());

    // Apply reactive rate limiter using transformDeferred
    return service.processPayment(request.getAmount(), request.getCurrency())
        .transformDeferred(RateLimiterOperator.of(rateLimiterRegistry.rateLimiter("ingestionLimiter")))
        .onErrorResume(io.github.resilience4j.ratelimiter.RequestNotPermitted.class, ex -> {
          log.warn("Rate limit exceeded for currency: {}", request.getCurrency());
          return Mono.error(new ResponseStatusException(
              HttpStatus.TOO_MANY_REQUESTS,
              "System is currently busy. Please try again later."
          ));
        });
  }

  /**
   * DTO for the request body.
   * Validates input before it reaches the service logic.
   */
  @Data
  public static class TransactionRequest {
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be positive")
    private BigDecimal amount;

    @NotNull(message = "Currency is required")
    private String currency;
  }
}
