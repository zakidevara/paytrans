package com.devara.paytrans.payment.transaction;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface AccountRepository extends ReactiveCrudRepository<Account, Long> {
  
  /**
   * Find account by account number for transfers.
   */
  Mono<Account> findByAccountNumber(String accountNumber);
}
