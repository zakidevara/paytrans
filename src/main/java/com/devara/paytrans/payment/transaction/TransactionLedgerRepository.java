package com.devara.paytrans.payment.transaction;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface TransactionLedgerRepository extends ReactiveCrudRepository<TransactionLedger, Long> {
  
  /**
   * Find all ledger entries for a transaction (audit trail).
   */
  Flux<TransactionLedger> findByTransactionIdOrderByChangedAtAsc(Long transactionId);
}
