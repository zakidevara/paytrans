package com.devara.paytrans.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Configuration for Redis with reactive support
 */
@Configuration
public class RedisConfig {

  /**
   * Creates a reactive Redis template for String-String operations
   * Used for idempotency key-value storage
   */
  @Bean
  @Primary
  public ReactiveRedisTemplate<String, String> reactiveRedisTemplate(
      ReactiveRedisConnectionFactory connectionFactory) {
    
    StringRedisSerializer serializer = new StringRedisSerializer();
    
    RedisSerializationContext<String, String> serializationContext = 
        RedisSerializationContext.<String, String>newSerializationContext(serializer)
            .key(serializer)
            .value(serializer)
            .hashKey(serializer)
            .hashValue(serializer)
            .build();
    
    return new ReactiveRedisTemplate<>(connectionFactory, serializationContext);
  }

  /**
   * ObjectMapper bean for JSON serialization/deserialization
   * Only created if no other ObjectMapper bean exists
   * Configured to handle Java 8 date/time types
   */
  @Bean
  @ConditionalOnMissingBean
  public ObjectMapper objectMapper() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
    mapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    return mapper;
  }
}
