package com.devara.paytrans.payment.transaction;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for Redis-based idempotency functionality.
 * Tests verify that duplicate requests are properly handled using Redis caching.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class IdempotencyIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ReactiveRedisTemplate<String, String> redisTemplate;

    @BeforeEach
    void setUp() {
        // Clear Redis before each test
        redisTemplate.getConnectionFactory()
            .getReactiveConnection()
            .serverCommands()
            .flushAll()
            .block();
    }

    @Test
    void testIdempotency_duplicateRequest_returnsSameTransaction() {
        // Given: A unique idempotency key and transaction request
        String idempotencyKey = "test-idempotency-key-001";
        TransactionController.TransactionRequest request = new TransactionController.TransactionRequest();
        request.setIdempotencyKey(idempotencyKey);
        request.setAmount(new BigDecimal("100.50"));
        request.setCurrency("USD");

        // When: First request is made
        Transaction firstResponse = webTestClient.post()
            .uri("/api/v1/transactions")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isCreated()
            .expectBody(Transaction.class)
            .returnResult()
            .getResponseBody();

        // Then: Transaction is created
        assertThat(firstResponse).isNotNull();
        assertThat(firstResponse.getId()).isNotNull();
        Long firstTransactionId = firstResponse.getId();

        // When: Duplicate request is made with same idempotency key
        Transaction secondResponse = webTestClient.post()
            .uri("/api/v1/transactions")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isCreated()
            .expectBody(Transaction.class)
            .returnResult()
            .getResponseBody();

        // Then: Same transaction is returned (idempotency check passed)
        assertThat(secondResponse).isNotNull();
        assertThat(secondResponse.getId()).isEqualTo(firstTransactionId);
        assertThat(secondResponse.getAmount()).isEqualTo(firstResponse.getAmount());
        assertThat(secondResponse.getCurrency()).isEqualTo(firstResponse.getCurrency());

        // Verify Redis cache contains the result
        String redisKey = "idempotency:" + idempotencyKey;
        String cachedValue = redisTemplate.opsForValue().get(redisKey).block();
        assertThat(cachedValue).isNotNull();
        assertThat(cachedValue).contains("\"id\":" + firstTransactionId);
    }

    @Test
    void testIdempotency_differentKeys_createsDifferentTransactions() {
        // Given: Two different idempotency keys
        String key1 = "test-key-001";
        String key2 = "test-key-002";

        TransactionController.TransactionRequest request1 = new TransactionController.TransactionRequest();
        request1.setIdempotencyKey(key1);
        request1.setAmount(new BigDecimal("50.00"));
        request1.setCurrency("USD");

        TransactionController.TransactionRequest request2 = new TransactionController.TransactionRequest();
        request2.setIdempotencyKey(key2);
        request2.setAmount(new BigDecimal("75.00"));
        request2.setCurrency("EUR");

        // When: Requests made with different keys
        Transaction response1 = webTestClient.post()
            .uri("/api/v1/transactions")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request1)
            .exchange()
            .expectStatus().isCreated()
            .expectBody(Transaction.class)
            .returnResult()
            .getResponseBody();

        Transaction response2 = webTestClient.post()
            .uri("/api/v1/transactions")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request2)
            .exchange()
            .expectStatus().isCreated()
            .expectBody(Transaction.class)
            .returnResult()
            .getResponseBody();

        // Then: Different transactions are created
        assertThat(response1).isNotNull();
        assertThat(response2).isNotNull();
        assertThat(response1.getId()).isNotEqualTo(response2.getId());
        assertThat(response1.getAmount()).isEqualTo(new BigDecimal("50.00"));
        assertThat(response2.getAmount()).isEqualTo(new BigDecimal("75.00"));
    }

    @Test
    void testIdempotency_missingKey_returnsValidationError() {
        // Given: Request without idempotency key
        TransactionController.TransactionRequest request = new TransactionController.TransactionRequest();
        request.setAmount(new BigDecimal("100.00"));
        request.setCurrency("USD");
        // idempotencyKey is null

        // When/Then: Request fails validation
        webTestClient.post()
            .uri("/api/v1/transactions")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isBadRequest();
    }

    @Test
    void testIdempotency_cachedResultExpiry() throws InterruptedException {
        // Note: This test would take 24 hours to run in real scenario
        // This is a demonstration of the concept
        // In production, you might use a shorter TTL for testing

        String idempotencyKey = "test-expiry-key";
        TransactionController.TransactionRequest request = new TransactionController.TransactionRequest();
        request.setIdempotencyKey(idempotencyKey);
        request.setAmount(new BigDecimal("200.00"));
        request.setCurrency("GBP");

        // Create transaction
        webTestClient.post()
            .uri("/api/v1/transactions")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isCreated();

        // Verify key exists in Redis
        String redisKey = "idempotency:" + idempotencyKey;
        Boolean exists = redisTemplate.hasKey(redisKey).block();
        assertThat(exists).isTrue();

        // Check TTL is set (should be around 86400 seconds = 24 hours)
        Long ttl = redisTemplate.getExpire(redisKey).block();
        assertThat(ttl).isGreaterThan(86000L); // Allow some margin
    }
}
