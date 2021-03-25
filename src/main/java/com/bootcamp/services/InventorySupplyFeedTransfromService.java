package com.bootcamp.services;

import com.bootcamp.dto.InventoryKafkaPayload;
import com.bootcamp.dto.InventoryUpdateKey;
import com.bootcamp.dto.InventoryUpdatePayload;
import com.bootcamp.dto.InventoryUpdateValue;
import com.bootcamp.dto.SupplyTo;
import com.bootcamp.dto.SupplyToPayload;
import com.bootcamp.dto.SupplyType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.kafka.receiver.ReceiverRecord;
@Component
public class InventorySupplyFeedTransfromService<K,V> {
  @Value("${ypfp.customer-name}")
  private String customerName;
  @Value("${streamer.inventory-target-topic}")
  private String consumerTopicName;

  public Mono<InventoryKafkaPayload> transformSupplyFeed(ReceiverRecord<K,V> record) {
    InventoryKafkaPayload inventoryKafkaPayload = new InventoryKafkaPayload();
    InventoryUpdateKey key = record.key()==null?null:(InventoryUpdateKey) record.key();
    InventoryUpdateValue value = record.value()==null?null:(InventoryUpdateValue) record.value();
    if(key!=null||value!=null){
      inventoryKafkaPayload.setIsFullyQualifiedTopicName(false);
      inventoryKafkaPayload.setKey(key);
      inventoryKafkaPayload.setOperation("CREATE");
      inventoryKafkaPayload.setTopic(consumerTopicName);
      inventoryKafkaPayload.setValue(transformUpdatePayload(value));
      inventoryKafkaPayload.setAudit(value.getAudit());
    }
    return Mono.just(inventoryKafkaPayload);
  }
  private InventoryUpdatePayload transformUpdatePayload(InventoryUpdateValue value) {
    InventoryUpdatePayload updatedValue = new InventoryUpdatePayload();
    updatedValue.setOrgId(value.getOrgId());
    updatedValue.setLocationId(value.getLocationId());
    updatedValue.setLocationType(value.getLocationType());
    updatedValue.setEventType(value.getEventType());
    updatedValue.setFeedType(value.getFeedType());
    updatedValue.setProductId(value.getProductId());
    updatedValue.setUom(value.getUom());
    Double quantity = calculateQuantity(value.getTo());
    updatedValue.setQuantity(quantity);
    SupplyToPayload supplyToPayload = SupplyToPayload.builder().build();
    supplyToPayload.setSupplyType("ONHAND");
    updatedValue.setTo(supplyToPayload);
    updatedValue.setAudit(value.getAudit());
    updatedValue.setUpdateTimeStamp(value.getUpdateTimeStamp());
    updatedValue.setOverrideZoneTransitionRule(value.getOverrideZoneTransitionRule());
    return updatedValue;
  }
  private Double calculateQuantity(SupplyTo to) {
    Double onhandQuantity=0.0;
    for(SupplyType supply : to.getSupplyTypes()) {
      if(supply.getSupplyType().equalsIgnoreCase("SOH"))
        onhandQuantity+=supply.getQuantity();
      else
        onhandQuantity-=supply.getQuantity();
    }
    return onhandQuantity;
  }

}
