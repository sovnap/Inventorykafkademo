package com.bootcamp.consumer;

import static org.apache.kafka.clients.consumer.ConsumerConfig.GROUP_ID_CONFIG;

import com.bootcamp.configuration.KafkaConsumerConfig;
import com.bootcamp.dto.KafkaConsumerConfigurationProperties;
import com.bootcamp.configuration.KafkaProducerConfig;
import com.bootcamp.dto.InventoryKafkaPayload;
import com.bootcamp.dto.InventoryUpdateKey;
import com.bootcamp.dto.InventoryUpdatePayload;
import com.bootcamp.dto.InventoryUpdateValue;
import com.bootcamp.dto.SupplyTo;
import com.bootcamp.dto.SupplyToPayload;
import com.bootcamp.dto.SupplyType;
import com.bootcamp.producer.CommonKafKaProducerService;
import com.bootcamp.services.InventorySupplyFeedTransfromService;
import com.bootcamp.services.KafkaRestProxyClientService;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverOffset;
import reactor.kafka.receiver.ReceiverOptions;
import reactor.kafka.receiver.ReceiverRecord;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderOptions;

@Component
public class InventoryFeedKafkaConsumer<K,V,R> {
  private final Logger LOG = LoggerFactory.getLogger(InventoryFeedKafkaConsumer.class);
  private KafkaConsumerConfigurationProperties config;
  @Autowired
  private CommonKafKaProducerService dlqSender;
  @Autowired
  private KafkaConsumerConfig kafkaConsumerConfig;
  @Autowired
  private KafkaProducerConfig kafkaProducerConfig;
  @Autowired
  private InventorySupplyFeedTransfromService inventorySupplyFeedTransfromService;

  @Value("${kafka.consumer.commit.interval:5s}")
  private Duration commitInterval;
  @Value("${streamer.inventory-source-topic}")
  private String topicName;
  @Autowired
  protected Environment env;
  @Value("${streamer.dlq-topic}")
  private String dlqTopicName;
  @Autowired
  private KafkaRestProxyClientService clientService;


  @EventListener(ApplicationReadyEvent.class)
  protected void consume() {
    config = generateKafkaConsumerConfigurationProperties(topicName);
    Map<String, Object> consumerProps = new HashMap<>();
    consumerProps.putAll(kafkaConsumerConfig.getConsumer());
    configureConsumerProperties(consumerProps, config.getGroupId());
    ReceiverOptions<K,V> receiverOptions =
        ReceiverOptions.<K, V>create(consumerProps)
            .subscription(Collections.singleton(topicName))
            .commitInterval(commitInterval)
            .addAssignListener(p -> LOG.info("Group partitions assigned: {}", p))
            .addRevokeListener(p -> LOG.info("Group partitions revoked: {}", p));
    receiveKafkaReceiver(receiverOptions);
  }

  private void receiveKafkaReceiver(ReceiverOptions<K,V> receiverOptions) {
    Flux<ReceiverRecord<K, V>> kafkaFlux = KafkaReceiver.create(receiverOptions).receive();
    kafkaFlux.subscribe(record->{
      try {
        ReceiverOffset offset = record.receiverOffset();
        LOG.info("Received record: {}", record);
        Mono<InventoryKafkaPayload> inventoryKafkaPayload = inventorySupplyFeedTransfromService.transformSupplyFeed(record);
        clientService.createLoad(inventoryKafkaPayload).subscribe();
      }catch (Exception e){
        LOG.info("Received exception: {}", e.getMessage());
        dlqSender.postMessageToTopic(record,dlqTopicName);
      }
    });
  }

  private KafkaConsumerConfigurationProperties generateKafkaConsumerConfigurationProperties(String topicName) {
    return KafkaConsumerConfigurationProperties.builder()
        .consumerEnabled(env.getProperty(String.format("kafka.consumer.override.%s.enabled", topicName), Boolean.class, true))
        .groupId(env.getProperty("override.group-id." + topicName))
        .build();
  }


  protected void configureConsumerProperties(Map<String, Object> consumerProps, String groupId) {
    consumerProps.put(JsonDeserializer.KEY_DEFAULT_TYPE, InventoryUpdateKey.class);
    consumerProps.put(JsonDeserializer.VALUE_DEFAULT_TYPE, InventoryUpdateValue.class);
    if (groupId != null) {
      consumerProps.put(GROUP_ID_CONFIG, groupId);
    }
  }
}
