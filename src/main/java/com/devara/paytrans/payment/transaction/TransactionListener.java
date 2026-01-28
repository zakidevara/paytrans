package com.devara.paytrans.payment.transaction;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class TransactionListener {

  @KafkaListener(topics = "transaction-events", groupId = "paytrans-group")
  public void handleTransactionEvent(TransactionEvent event) {
    log.info("Received Kafka Event: Processing fraud check for Tx ID: {}", event.getId());
    // Simulate heavy processing
    try {
      Thread.sleep(100);
    } catch (InterruptedException e) {

    }
  }
}
