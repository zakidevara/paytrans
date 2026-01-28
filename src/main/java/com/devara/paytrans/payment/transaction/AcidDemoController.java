package com.devara.paytrans.payment.transaction;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.Map;

/**
 * REST Controller for demonstrating ACID principles.
 * 
 * Endpoints:
 * - POST /api/v1/acid/atomicity/transfer      - Atomic money transfer
 * - POST /api/v1/acid/atomicity/multistep     - Multi-step with optional failure
 * - POST /api/v1/acid/consistency             - Validated transaction
 * - PUT  /api/v1/acid/isolation/{id}          - Optimistic locking update
 * - POST /api/v1/acid/isolation/serializable  - Serializable isolation
 * - POST /api/v1/acid/durability              - Transaction with audit
 * - GET  /api/v1/acid/durability/audit/{id}   - Get audit trail
 */
@RestController
@RequestMapping("/api/v1/acid")
@RequiredArgsConstructor
@Slf4j
public class AcidDemoController {

  private final AcidTransactionService acidService;

  // ============================================================
  // ATOMICITY ENDPOINTS
  // ============================================================

  /**
   * ATOMICITY: Transfer money between two accounts.
   * Both debit and credit happen atomically or not at all.
   */
  @PostMapping("/atomicity/transfer")
  public Mono<ResponseEntity<Object>> atomicTransfer(@Valid @RequestBody TransferRequest request) {
    log.info("API: Atomic transfer request received");
    
    return acidService.atomicTransfer(
            request.getFromAccount(), 
            request.getToAccount(), 
            request.getAmount())
        .map(tx -> ResponseEntity.status(HttpStatus.CREATED).body((Object) Map.of(
            "success", true,
            "message", "Transfer completed atomically",
            "transactionId", tx.getId(),
            "amount", tx.getAmount(),
            "principle", "ATOMICITY - All operations succeeded together"
        )))
        .onErrorResume(Account.InsufficientFundsException.class, e -> 
            Mono.just(ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage(),
                "principle", "ATOMICITY - Transfer rolled back due to insufficient funds"
            ))))
        .onErrorResume(e -> 
            Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false,
                "error", e.getMessage(),
                "principle", "ATOMICITY - All changes rolled back on failure"
            ))));
  }

  /**
   * ATOMICITY: Multi-step operation with optional failure simulation.
   */
  @PostMapping("/atomicity/multistep")
  public Mono<ResponseEntity<Object>> atomicMultiStep(@Valid @RequestBody MultiStepRequest request) {
    log.info("API: Multi-step operation request (simulateFailure={})", request.isSimulateFailure());
    
    return acidService.atomicMultiStepWithFailure(request.getAmount(), request.isSimulateFailure())
        .map(tx -> ResponseEntity.ok((Object) Map.of(
            "success", true,
            "message", "All steps completed successfully",
            "transactionId", tx != null ? tx.getId() : "N/A",
            "principle", "ATOMICITY - All 3 steps committed together"
        )))
        .onErrorResume(e -> 
            Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false,
                "error", e.getMessage(),
                "principle", "ATOMICITY - All steps rolled back on failure",
                "note", "Check database - no partial data exists"
            ))));
  }

  // ============================================================
  // CONSISTENCY ENDPOINT
  // ============================================================

  /**
   * CONSISTENCY: Create a transaction with full business rule validation.
   */
  @PostMapping("/consistency")
  public Mono<ResponseEntity<Object>> consistentTransaction(@Valid @RequestBody ConsistencyRequest request) {
    log.info("API: Consistency validation request");
    
    return acidService.consistentTransaction(request.getAmount(), request.getCurrency())
        .map(tx -> ResponseEntity.status(HttpStatus.CREATED).body((Object) Map.of(
            "success", true,
            "transactionId", tx.getId(),
            "amount", tx.getAmount(),
            "fee", tx.getFee(),
            "netAmount", tx.getNetAmount(),
            "currency", tx.getCurrency(),
            "principle", "CONSISTENCY - All business rules validated before commit"
        )))
        .onErrorResume(AcidTransactionService.ConsistencyViolationException.class, e ->
            Mono.just(ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage(),
                "principle", "CONSISTENCY - Invalid data rejected to maintain valid state"
            ))));
  }

  // ============================================================
  // ISOLATION ENDPOINTS
  // ============================================================

  /**
   * ISOLATION: Update transaction with optimistic locking.
   */
  @PutMapping("/isolation/{id}")
  public Mono<ResponseEntity<Object>> isolatedUpdate(
      @PathVariable Long id,
      @RequestBody IsolationRequest request) {
    log.info("API: Isolated update request for transaction {}", id);
    
    return acidService.isolatedUpdate(id, request.getNewStatus())
        .map(tx -> ResponseEntity.ok((Object) Map.of(
            "success", true,
            "transactionId", tx.getId(),
            "newStatus", tx.getStatus(),
            "version", tx.getVersion(),
            "principle", "ISOLATION - Optimistic locking prevented concurrent modification"
        )))
        .onErrorResume(AcidTransactionService.ConcurrentModificationException.class, e ->
            Mono.just(ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "success", false,
                "error", e.getMessage(),
                "principle", "ISOLATION - Concurrent modification detected, please retry"
            ))))
        .onErrorResume(e ->
            Mono.just(ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ))));
  }

  /**
   * ISOLATION: Serializable transaction (highest isolation level).
   */
  @PostMapping("/isolation/serializable")
  public Mono<ResponseEntity<Object>> serializableTransaction(@Valid @RequestBody ConsistencyRequest request) {
    log.info("API: Serializable isolation request");
    
    return acidService.serializedCriticalOperation(request.getAmount(), request.getCurrency())
        .map(tx -> ResponseEntity.status(HttpStatus.CREATED).body((Object) Map.of(
            "success", true,
            "transactionId", tx.getId(),
            "amount", tx.getAmount(),
            "principle", "ISOLATION - SERIALIZABLE level ensures complete isolation"
        )));
  }

  // ============================================================
  // DURABILITY ENDPOINTS
  // ============================================================

  /**
   * DURABILITY: Create transaction with full audit trail.
   */
  @PostMapping("/durability")
  public Mono<ResponseEntity<Object>> durableTransaction(@Valid @RequestBody DurabilityRequest request) {
    log.info("API: Durable transaction with audit trail request");
    
    return acidService.durableTransactionWithAudit(
            request.getAmount(), 
            request.getCurrency(), 
            request.getReason())
        .map(tx -> ResponseEntity.status(HttpStatus.CREATED).body((Object) Map.of(
            "success", true,
            "transactionId", tx.getId(),
            "amount", tx.getAmount(),
            "principle", "DURABILITY - Transaction and audit trail persisted to WAL",
            "note", "Data will survive system crashes"
        )));
  }

  /**
   * DURABILITY: Get audit trail for a transaction.
   */
  @GetMapping("/durability/audit/{id}")
  public Mono<ResponseEntity<Object>> getAuditTrail(@PathVariable Long id) {
    log.info("API: Audit trail request for transaction {}", id);
    
    return acidService.getAuditTrail(id)
        .map(trail -> ResponseEntity.ok((Object) Map.of(
            "transactionId", id,
            "auditTrail", trail,
            "principle", "DURABILITY - Complete history preserved for recovery"
        )));
  }

  // ============================================================
  // REQUEST DTOs
  // ============================================================

  @Data
  public static class TransferRequest {
    @NotBlank(message = "Source account is required")
    private String fromAccount;
    
    @NotBlank(message = "Destination account is required")
    private String toAccount;
    
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be positive")
    private BigDecimal amount;
  }

  @Data
  public static class MultiStepRequest {
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be positive")
    private BigDecimal amount;
    
    private boolean simulateFailure;
  }

  @Data
  public static class ConsistencyRequest {
    @NotNull(message = "Amount is required")
    private BigDecimal amount;
    
    @NotBlank(message = "Currency is required")
    private String currency;
  }

  @Data
  public static class IsolationRequest {
    @NotBlank(message = "New status is required")
    private String newStatus;
  }

  @Data
  public static class DurabilityRequest {
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be positive")
    private BigDecimal amount;
    
    @NotBlank(message = "Currency is required")
    private String currency;
    
    private String reason;
  }
}
