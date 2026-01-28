package com.devara.paytrans.config;

import com.devara.paytrans.payment.transaction.TransactionEvent;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConfig {


  @Bean
  public KafkaTemplate<String, TransactionEvent> kafkaTemplate() {
    return new KafkaTemplate<>(producerFactory());
  }

  @Bean
  public ProducerFactory<String, TransactionEvent> producerFactory() {
    Map<String, Object> configProps = new HashMap<>();

    // 1. The address
    configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");

    // 2. Serializers (Ensure these are correctly imported)
    configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

    // 3. Identification (Often required in newer Kafka clients to construct the producer)
    configProps.put(ProducerConfig.CLIENT_ID_CONFIG, "paytrans-producer");

    // 4. Resilience settings
    configProps.put(ProducerConfig.RETRIES_CONFIG, 3);
    configProps.put(ProducerConfig.ACKS_CONFIG, "all");

    return new DefaultKafkaProducerFactory<>(configProps);
  }
}