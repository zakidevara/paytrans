package com.devara.paytrans.config;

import com.devara.paytrans.payment.transaction.TransactionEvent;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderOptions;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConfig {

  @Bean
  public SenderOptions<String, TransactionEvent> senderOptions() {
    Map<String, Object> configProps = new HashMap<>();

    // 1. The address
    configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");

    // 2. Serializers - Using custom Jackson serializer instead of deprecated JsonSerializer
    configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JacksonSerializer.class);

    // 3. Identification
    configProps.put(ProducerConfig.CLIENT_ID_CONFIG, "paytrans-producer");

    // 4. Resilience settings
    configProps.put(ProducerConfig.RETRIES_CONFIG, 3);
    configProps.put(ProducerConfig.ACKS_CONFIG, "all");

    return SenderOptions.create(configProps);
  }

  @Bean
  public KafkaSender<String, TransactionEvent> kafkaSender(SenderOptions<String, TransactionEvent> senderOptions) {
    return KafkaSender.create(senderOptions);
  }
}
