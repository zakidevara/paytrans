package com.devara.paytrans.payment.transaction;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.math.BigDecimal;
import java.time.Instant;

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
  private Instant createdAt;
}