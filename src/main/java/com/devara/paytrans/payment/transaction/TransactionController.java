package com.devara.paytrans.payment.transaction;

import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
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

  /**
   * Creates a new transaction.
   * Protected by a Rate Limiter to prevent system overload.
   */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @RateLimiter(name = "ingestionLimiter", fallbackMethod = "fallback")
  public Mono<Transaction> createTransaction(@Valid @RequestBody TransactionRequest request) {
    log.info("Received transaction request for amount: {}", request.getAmount());
    return service.processPayment(request.getAmount(), request.getCurrency());
  }

  /**
   * Fallback method called when the Rate Limiter blocks a request.
   * MUST have the same signature as the original method + the Exception parameter.
   */
  public Mono<Transaction> fallback(TransactionRequest request, RequestNotPermitted ex) {
    log.warn("Rate limit exceeded for currency: {}", request.getCurrency());
    return Mono.error(new ResponseStatusException(
        HttpStatus.TOO_MANY_REQUESTS,
        "System is currently busy. Please try again later."
    ));
  }

  /**
   * DTO for the request body.
   * Validates input before it reaches the service logic.
   */
  @Data
  static class TransactionRequest {
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be positive")
    private BigDecimal amount;

    @NotNull(message = "Currency is required")
    private String currency;
  }
}