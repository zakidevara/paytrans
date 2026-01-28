package com.devara.paytrans.payment.transaction;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * ============================================================
 * ACID TRANSACTION SERVICE - Demonstrates all ACID principles
 * ============================================================
 * 
 * A = ATOMICITY
 *     All operations within a transaction succeed or fail together.
 *     If any step fails, the entire transaction is rolled back.
 * 
 * C = CONSISTENCY
 *     Data transitions from one valid state to another.
 *     Business rules and constraints are always enforced.
 * 
 * I = ISOLATION
 *     Concurrent transactions don't interfere with each other.
 *     Uses optimistic locking to detect conflicts.
 * 
 * D = DURABILITY
 *     Once committed, data survives system failures.
 *     Audit trail provides complete transaction history.
 * ============================================================
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AcidTransactionService {

  private final TransactionRepository transactionRepository;
  private final AccountRepository accountRepository;
  private final TransactionLedgerRepository ledgerRepository;

  // ============================================================
  // ATOMICITY DEMONSTRATION
  // All operations succeed or all fail together
  // ============================================================
  
  /**
   * ATOMIC Transfer: Move money between two accounts.
   * 
   * Either BOTH the debit AND credit succeed, or NEITHER happens.
   * This prevents the "lost money" scenario where money is debited
   * from one account but never credited to another.
   * 
   * @param fromAccount Source account number
   * @param toAccount   Destination account number
   * @param amount      Amount to transfer
   * @return The completed transaction
   */
  @Transactional
  public Mono<Transaction> atomicTransfer(String fromAccount, String toAccount, BigDecimal amount) {
    log.info("=== ATOMICITY DEMO: Starting atomic transfer ===");
    log.info("From: {}, To: {}, Amount: {}", fromAccount, toAccount, amount);
    
    return Mono.zip(
        accountRepository.findByAccountNumber(fromAccount)
            .switchIfEmpty(Mono.error(new IllegalArgumentException("Source account not found: " + fromAccount))),
        accountRepository.findByAccountNumber(toAccount)
            .switchIfEmpty(Mono.error(new IllegalArgumentException("Destination account not found: " + toAccount)))
    )
    .flatMap(accounts -> {
      Account source = accounts.getT1();
      Account destination = accounts.getT2();
      
      log.info("Before transfer - Source balance: {}, Destination balance: {}", 
          source.getBalance(), destination.getBalance());
      
      // ATOMICITY: Both operations are part of the same transaction
      // If either fails, both are rolled back
      try {
        source.debit(amount);      // Step 1: Debit source
        destination.credit(amount); // Step 2: Credit destination
      } catch (Account.InsufficientFundsException e) {
        log.error("ATOMICITY: Transfer failed - rolling back. Reason: {}", e.getMessage());
        return Mono.error(e);
      }
      
      // Save both accounts atomically
      return accountRepository.save(source)
          .then(accountRepository.save(destination))
          .then(createTransactionWithLedger(amount, "USD", "TRANSFER: " + fromAccount + " -> " + toAccount))
          .doOnSuccess(tx -> {
            log.info("ATOMICITY: Transfer completed successfully");
            log.info("After transfer - Source balance: {}, Destination balance: {}", 
                source.getBalance(), destination.getBalance());
          })
          .doOnError(error -> {
            log.error("ATOMICITY: Transfer failed, all changes rolled back: {}", error.getMessage());
          });
    });
  }

  /**
   * ATOMIC Multi-step operation with simulated failure.
   * Demonstrates rollback when an intermediate step fails.
   */
  @Transactional
  public Mono<Transaction> atomicMultiStepWithFailure(BigDecimal amount, boolean simulateFailure) {
    log.info("=== ATOMICITY DEMO: Multi-step operation (simulateFailure={}) ===", simulateFailure);
    
    // Step 1: Create transaction in PENDING state
    return createTransaction(amount, "USD", Transaction.STATUS_PENDING)
        .flatMap(tx -> {
          log.info("Step 1 COMPLETED: Transaction created with ID: {}", tx.getId());
          
          // Step 2: Update to PROCESSING
          tx.setStatus(Transaction.STATUS_PROCESSING);
          tx.setUpdatedAt(Instant.now());
          return transactionRepository.save(tx);
        })
        .flatMap(tx -> {
          log.info("Step 2 COMPLETED: Transaction status updated to PROCESSING");
          
          // Step 3: Simulate failure if requested
          if (simulateFailure) {
            log.error("Step 3 FAILED: Simulated failure - triggering rollback!");
            return Mono.error(new RuntimeException("Simulated failure for ATOMICITY demonstration"));
          }
          
          // Step 3: Complete the transaction
          tx.setStatus(Transaction.STATUS_COMPLETED);
          tx.setUpdatedAt(Instant.now());
          return transactionRepository.save(tx);
        })
        .flatMap(tx -> {
          log.info("Step 3 COMPLETED: Transaction completed");
          return writeLedger(tx.getId(), null, Transaction.STATUS_COMPLETED, null, tx.getAmount(), "Transaction completed");
        })
        .doOnSuccess(ledger -> log.info("ATOMICITY: All steps completed successfully"))
        .doOnError(error -> log.error("ATOMICITY: Operation failed - ALL changes rolled back"))
        .then(Mono.defer(() -> transactionRepository.findById(1L).switchIfEmpty(Mono.empty())));
  }

  // ============================================================
  // CONSISTENCY DEMONSTRATION
  // Data always remains in a valid state
  // ============================================================
  
  /**
   * CONSISTENCY: Validates all business rules before persistence.
   * 
   * Rules enforced:
   * 1. Amount must be positive
   * 2. Currency must be valid (USD, EUR, GBP, JPY, IDR)
   * 3. Fee cannot exceed amount
   * 4. Net amount must be positive
   */
  @Transactional
  public Mono<Transaction> consistentTransaction(BigDecimal amount, String currency) {
    log.info("=== CONSISTENCY DEMO: Validating business rules ===");
    
    // Rule 1: Amount must be positive
    if (amount.compareTo(BigDecimal.ZERO) <= 0) {
      log.error("CONSISTENCY VIOLATION: Amount must be positive. Got: {}", amount);
      return Mono.error(new ConsistencyViolationException("Amount must be positive"));
    }
    
    // Rule 2: Currency must be valid
    if (!isValidCurrency(currency)) {
      log.error("CONSISTENCY VIOLATION: Invalid currency: {}", currency);
      return Mono.error(new ConsistencyViolationException("Invalid currency: " + currency));
    }
    
    // Rule 3: Calculate fee (2.5% + $0.30)
    BigDecimal fee = amount.multiply(BigDecimal.valueOf(0.025))
        .add(BigDecimal.valueOf(0.30))
        .setScale(2, BigDecimal.ROUND_HALF_UP);
    
    // Rule 4: Net amount must be positive
    BigDecimal netAmount = amount.subtract(fee);
    if (netAmount.compareTo(BigDecimal.ZERO) <= 0) {
      log.error("CONSISTENCY VIOLATION: Net amount would be non-positive. Amount: {}, Fee: {}", amount, fee);
      return Mono.error(new ConsistencyViolationException("Amount too small to cover fees"));
    }
    
    log.info("CONSISTENCY: All validations passed. Amount: {}, Fee: {}, Net: {}", amount, fee, netAmount);
    
    Transaction tx = Transaction.builder()
        .amount(amount)
        .currency(currency)
        .status(Transaction.STATUS_COMPLETED)
        .fee(fee)
        .netAmount(netAmount)
        .createdAt(Instant.now())
        .updatedAt(Instant.now())
        .build();
    
    return transactionRepository.save(tx)
        .flatMap(savedTx -> writeLedger(savedTx.getId(), null, Transaction.STATUS_COMPLETED, null, amount, "Consistent transaction created")
            .thenReturn(savedTx))
        .doOnSuccess(savedTx -> log.info("CONSISTENCY: Transaction saved with valid state. ID: {}", savedTx.getId()));
  }
  
  private boolean isValidCurrency(String currency) {
    return currency != null && 
        (currency.equals("USD") || currency.equals("EUR") || 
         currency.equals("GBP") || currency.equals("JPY") || currency.equals("IDR"));
  }

  // ============================================================
  // ISOLATION DEMONSTRATION
  // Concurrent transactions don't interfere with each other
  // ============================================================
  
  /**
   * ISOLATION: Uses optimistic locking to detect concurrent modifications.
   * 
   * The @Version field in Transaction entity is used to detect if another
   * transaction has modified the same row. If so, OptimisticLockingFailureException
   * is thrown and the operation can be retried.
   * 
   * Isolation Level: READ_COMMITTED (PostgreSQL default)
   * - Prevents dirty reads
   * - Allows non-repeatable reads (acceptable for most use cases)
   */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public Mono<Transaction> isolatedUpdate(Long transactionId, String newStatus) {
    log.info("=== ISOLATION DEMO: Optimistic locking update ===");
    log.info("Transaction ID: {}, New Status: {}", transactionId, newStatus);
    
    return transactionRepository.findById(transactionId)
        .switchIfEmpty(Mono.error(new IllegalArgumentException("Transaction not found: " + transactionId)))
        .flatMap(tx -> {
          String oldStatus = tx.getStatus();
          log.info("ISOLATION: Current version: {}, Status: {}", tx.getVersion(), oldStatus);
          
          // Simulate some processing time (increases chance of concurrent modification)
          tx.setStatus(newStatus);
          tx.setUpdatedAt(Instant.now());
          
          return transactionRepository.save(tx)
              .flatMap(savedTx -> writeLedger(savedTx.getId(), oldStatus, newStatus, savedTx.getAmount(), savedTx.getAmount(), "Status updated")
                  .thenReturn(savedTx))
              .doOnSuccess(savedTx -> log.info("ISOLATION: Update successful. New version: {}", savedTx.getVersion()))
              .onErrorResume(OptimisticLockingFailureException.class, e -> {
                log.warn("ISOLATION: Concurrent modification detected! Another transaction modified this row.");
                log.warn("ISOLATION: Retry the operation with fresh data.");
                return Mono.error(new ConcurrentModificationException("Transaction was modified by another process. Please retry."));
              });
        });
  }

  /**
   * ISOLATION: Serializable isolation for critical operations.
   * 
   * Uses SERIALIZABLE isolation level for operations that require
   * the highest level of isolation (e.g., financial reconciliation).
   * This prevents phantom reads and ensures complete isolation.
   */
  @Transactional(isolation = Isolation.SERIALIZABLE)
  public Mono<Transaction> serializedCriticalOperation(BigDecimal amount, String currency) {
    log.info("=== ISOLATION DEMO: SERIALIZABLE isolation level ===");
    log.info("This operation has the highest isolation level");
    
    return createTransactionWithLedger(amount, currency, "Critical operation with SERIALIZABLE isolation")
        .doOnSuccess(tx -> log.info("ISOLATION: Serializable transaction completed. ID: {}", tx.getId()));
  }

  // ============================================================
  // DURABILITY DEMONSTRATION
  // Once committed, data survives system failures
  // ============================================================
  
  /**
   * DURABILITY: Creates transaction with complete audit trail.
   * 
   * The ledger entry provides:
   * 1. Immutable record of what happened
   * 2. Timestamp for when it happened
   * 3. Who/what made the change
   * 4. Reason for the change
   * 
   * Even if the main transaction table is corrupted, the ledger
   * allows reconstruction of the transaction history.
   */
  @Transactional
  public Mono<Transaction> durableTransactionWithAudit(BigDecimal amount, String currency, String reason) {
    log.info("=== DURABILITY DEMO: Transaction with full audit trail ===");
    
    return createTransaction(amount, currency, Transaction.STATUS_COMPLETED)
        .flatMap(tx -> {
          log.info("DURABILITY: Transaction created. ID: {}", tx.getId());
          
          // Create immutable audit record
          return writeLedger(
              tx.getId(), 
              null, 
              Transaction.STATUS_COMPLETED, 
              null, 
              amount, 
              reason != null ? reason : "Transaction created via durable operation"
          ).thenReturn(tx);
        })
        .doOnSuccess(tx -> {
          log.info("DURABILITY: Audit trail created");
          log.info("DURABILITY: Data is now persisted to PostgreSQL WAL");
          log.info("DURABILITY: Even after system crash, this transaction will survive");
        });
  }

  /**
   * DURABILITY: Retrieve complete audit history for a transaction.
   */
  public Mono<String> getAuditTrail(Long transactionId) {
    log.info("=== DURABILITY DEMO: Retrieving audit trail for transaction {} ===", transactionId);
    
    return ledgerRepository.findByTransactionIdOrderByChangedAtAsc(transactionId)
        .collectList()
        .map(entries -> {
          StringBuilder sb = new StringBuilder();
          sb.append("AUDIT TRAIL FOR TRANSACTION ").append(transactionId).append("\n");
          sb.append("=".repeat(50)).append("\n");
          
          for (TransactionLedger entry : entries) {
            sb.append(String.format("[%s] %s: %s -> %s (Amount: %s) - %s%n",
                entry.getChangedAt(),
                entry.getOperation(),
                entry.getOldStatus() != null ? entry.getOldStatus() : "N/A",
                entry.getNewStatus(),
                entry.getNewAmount(),
                entry.getReason()
            ));
          }
          
          return sb.toString();
        });
  }

  // ============================================================
  // HELPER METHODS
  // ============================================================
  
  private Mono<Transaction> createTransaction(BigDecimal amount, String currency, String status) {
    Transaction tx = Transaction.builder()
        .amount(amount)
        .currency(currency)
        .status(status)
        .fee(BigDecimal.ZERO)
        .netAmount(amount)
        .createdAt(Instant.now())
        .updatedAt(Instant.now())
        .build();
    
    return transactionRepository.save(tx);
  }
  
  private Mono<Transaction> createTransactionWithLedger(BigDecimal amount, String currency, String reason) {
    return createTransaction(amount, currency, Transaction.STATUS_COMPLETED)
        .flatMap(tx -> writeLedger(tx.getId(), null, Transaction.STATUS_COMPLETED, null, amount, reason)
            .thenReturn(tx));
  }
  
  private Mono<TransactionLedger> writeLedger(Long transactionId, String oldStatus, String newStatus, 
                                               BigDecimal oldAmount, BigDecimal newAmount, String reason) {
    TransactionLedger ledger = TransactionLedger.builder()
        .transactionId(transactionId)
        .operation(oldStatus == null ? TransactionLedger.OP_CREATE : TransactionLedger.OP_UPDATE)
        .oldStatus(oldStatus)
        .newStatus(newStatus)
        .oldAmount(oldAmount)
        .newAmount(newAmount)
        .changedBy("SYSTEM")
        .changedAt(Instant.now())
        .reason(reason)
        .build();
    
    return ledgerRepository.save(ledger);
  }

  // ============================================================
  // CUSTOM EXCEPTIONS
  // ============================================================
  
  public static class ConsistencyViolationException extends RuntimeException {
    public ConsistencyViolationException(String message) {
      super(message);
    }
  }
  
  public static class ConcurrentModificationException extends RuntimeException {
    public ConcurrentModificationException(String message) {
      super(message);
    }
  }
}
