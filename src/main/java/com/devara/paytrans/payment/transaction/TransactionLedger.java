package com.devara.paytrans.payment.transaction;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Transaction Ledger for DURABILITY demonstration.
 * 
 * Provides an immutable audit trail of all transaction changes.
 * Once written, ledger entries are never modified or deleted.
 * This ensures complete traceability and supports:
 * - Disaster recovery
 * - Compliance auditing
 * - Transaction history reconstruction
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("transaction_ledger")
public class TransactionLedger {
  @Id
  private Long id;
  
  @Column("transaction_id")
  private Long transactionId;
  
  private String operation;  // CREATE, UPDATE, ROLLBACK
  
  @Column("old_status")
  private String oldStatus;
  
  @Column("new_status")
  private String newStatus;
  
  @Column("old_amount")
  private BigDecimal oldAmount;
  
  @Column("new_amount")
  private BigDecimal newAmount;
  
  @Column("changed_by")
  private String changedBy;
  
  @Column("changed_at")
  private Instant changedAt;
  
  private String reason;
  
  // Operation constants
  public static final String OP_CREATE = "CREATE";
  public static final String OP_UPDATE = "UPDATE";
  public static final String OP_ROLLBACK = "ROLLBACK";
}
