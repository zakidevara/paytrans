package com.devara.paytrans.payment.transaction;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Account entity for demonstrating ATOMIC transfers.
 * 
 * ACID Demonstration:
 * - Atomicity: Transfers between accounts succeed or fail together
 * - Consistency: Balance cannot go negative (CHECK constraint)
 * - Isolation: @Version prevents concurrent modification issues
 * - Durability: PostgreSQL guarantees persistence
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("accounts")
public class Account {
  @Id
  private Long id;
  
  @Column("account_number")
  private String accountNumber;
  
  private BigDecimal balance;
  
  private String currency;
  
  @Column("created_at")
  private Instant createdAt;
  
  @Column("updated_at")
  private Instant updatedAt;
  
  /**
   * Optimistic locking version for ISOLATION.
   * Ensures no lost updates during concurrent access.
   */
  @Version
  private Integer version;
  
  /**
   * Debit the account (subtract amount).
   * Throws exception if insufficient funds (CONSISTENCY).
   */
  public void debit(BigDecimal amount) {
    if (this.balance.compareTo(amount) < 0) {
      throw new InsufficientFundsException(
          "Insufficient funds in account " + accountNumber + 
          ". Available: " + balance + ", Required: " + amount
      );
    }
    this.balance = this.balance.subtract(amount);
    this.updatedAt = Instant.now();
  }
  
  /**
   * Credit the account (add amount).
   */
  public void credit(BigDecimal amount) {
    this.balance = this.balance.add(amount);
    this.updatedAt = Instant.now();
  }
  
  /**
   * Exception for insufficient funds (CONSISTENCY violation prevention)
   */
  public static class InsufficientFundsException extends RuntimeException {
    public InsufficientFundsException(String message) {
      super(message);
    }
  }
}
