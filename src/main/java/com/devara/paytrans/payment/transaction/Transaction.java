package com.devara.paytrans.payment.transaction;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Transaction entity demonstrating ACID principles:
 * 
 * - Atomicity: Used within @Transactional boundaries
 * - Consistency: Validated before persistence (amount > 0, valid currency/status)
 * - Isolation: @Version field enables optimistic locking for concurrent access
 * - Durability: Persisted to PostgreSQL with WAL guarantees
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("transactions")
public class Transaction {
  @Id
  private Long id;
  
  private BigDecimal amount;
  
  private String currency;
  
  private String status;
  
  private BigDecimal fee;
  
  @Column("net_amount")
  private BigDecimal netAmount;
  
  @Column("created_at")
  private Instant createdAt;
  
  @Column("updated_at")
  private Instant updatedAt;
  
  /**
   * Version field for optimistic locking (ISOLATION).
   * Prevents lost updates in concurrent transactions.
   * If two transactions try to update the same row,
   * the second one will fail with OptimisticLockingFailureException.
   */
  @Version
  private Integer version;
  
  /**
   * Transaction status constants for CONSISTENCY
   */
  public static final String STATUS_PENDING = "PENDING";
  public static final String STATUS_PROCESSING = "PROCESSING";
  public static final String STATUS_COMPLETED = "COMPLETED";
  public static final String STATUS_FAILED = "FAILED";
  public static final String STATUS_ROLLED_BACK = "ROLLED_BACK";
}