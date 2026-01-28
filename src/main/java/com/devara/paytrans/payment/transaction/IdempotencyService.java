package com.devara.paytrans.payment.transaction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Service to handle idempotent transaction processing using Redis.
 * Prevents duplicate transactions by caching results based on idempotency keys.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class IdempotencyService {

  private final ReactiveRedisTemplate<String, String> redisTemplate;
  private final ObjectMapper objectMapper;

  private static final String IDEMPOTENCY_PREFIX = "idempotency:";
  private static final String PROCESSING_SUFFIX = ":processing";
  private static final Duration TTL = Duration.ofHours(24); // Keep results for 24 hours
  private static final Duration PROCESSING_TTL = Duration.ofMinutes(5); // Lock duration

  /**
   * Executes an operation with idempotency guarantee.
   * If the idempotency key already exists:
   * - Returns cached result if operation completed
   * - Returns error if operation is still processing
   * If the key doesn't exist, executes the operation and caches the result
   *
   * @param idempotencyKey Unique key identifying this operation
   * @param operation The operation to execute
   * @return Mono containing the operation result (cached or fresh)
   */
  public Mono<Transaction> executeIdempotent(String idempotencyKey, Mono<Transaction> operation) {
    String resultKey = IDEMPOTENCY_PREFIX + idempotencyKey;
    String processingKey = resultKey + PROCESSING_SUFFIX;

    log.info("Checking idempotency for key: {}", idempotencyKey);

    // Step 1: Check if we already have a result
    return redisTemplate.opsForValue().get(resultKey)
        .flatMap(cachedResult -> {
          log.info("Found cached result for key: {}", idempotencyKey);
          return deserializeTransaction(cachedResult)
              .doOnSuccess(tx -> log.info("Returning cached transaction ID: {}", tx.getId()));
        })
        // Step 2: If no result, check if operation is processing
        .switchIfEmpty(Mono.defer(() -> 
            redisTemplate.opsForValue().setIfAbsent(processingKey, "true", PROCESSING_TTL)
                .flatMap(acquired -> {
                  if (Boolean.TRUE.equals(acquired)) {
                    log.info("Acquired processing lock for key: {}", idempotencyKey);
                    // We got the lock, execute the operation
                    return executeAndCache(idempotencyKey, resultKey, processingKey, operation);
                  } else {
                    log.warn("Duplicate request detected (processing) for key: {}", idempotencyKey);
                    // Another request is processing
                    return Mono.error(new DuplicateRequestException(
                        "A request with the same idempotency key is currently being processed. Please try again later."
                    ));
                  }
                })
        ));
  }

  /**
   * Executes the operation and caches the result in Redis
   */
  private Mono<Transaction> executeAndCache(String idempotencyKey, String resultKey, 
                                             String processingKey, Mono<Transaction> operation) {
    return operation
        .flatMap(transaction -> 
            // Serialize and cache the result
            serializeTransaction(transaction)
                .flatMap(serialized -> 
                    redisTemplate.opsForValue().set(resultKey, serialized, TTL)
                        .then(Mono.just(transaction))
                )
                .doOnSuccess(tx -> 
                    log.info("Cached transaction result for key: {} with ID: {}", 
                        idempotencyKey, tx.getId())
                )
        )
        .doFinally(signal -> {
          // Always release the processing lock
          redisTemplate.delete(processingKey)
              .subscribe(deleted -> 
                  log.info("Released processing lock for key: {}", idempotencyKey)
              );
        })
        .onErrorResume(error -> {
          log.error("Error during idempotent execution for key: {}", idempotencyKey, error);
          // Clean up on error
          return redisTemplate.delete(processingKey)
              .then(Mono.error(error));
        });
  }

  /**
   * Serializes a Transaction object to JSON string
   */
  private Mono<String> serializeTransaction(Transaction transaction) {
    return Mono.fromCallable(() -> {
      try {
        return objectMapper.writeValueAsString(transaction);
      } catch (JsonProcessingException e) {
        throw new RuntimeException("Failed to serialize transaction", e);
      }
    });
  }

  /**
   * Deserializes a JSON string to Transaction object
   */
  private Mono<Transaction> deserializeTransaction(String json) {
    return Mono.fromCallable(() -> {
      try {
        return objectMapper.readValue(json, Transaction.class);
      } catch (JsonProcessingException e) {
        throw new RuntimeException("Failed to deserialize transaction", e);
      }
    });
  }

  /**
   * Custom exception for duplicate requests
   */
  public static class DuplicateRequestException extends RuntimeException {
    public DuplicateRequestException(String message) {
      super(message);
    }
  }
}
