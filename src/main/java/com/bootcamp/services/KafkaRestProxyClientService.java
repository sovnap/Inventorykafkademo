package com.bootcamp.services;

import com.bootcamp.dto.InventoryKafkaPayload;
import com.bootcamp.dto.InventoryUpdatePayload;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
@Service
public class KafkaRestProxyClientService {

 /* @Autowired
  private WebClient webClient;*/

  @Value("${common.services.url}")
  private String commonServicesUrl;

  public Mono<? extends InventoryKafkaPayload> createLoad(Mono<InventoryKafkaPayload> payload) {
    return WebClient.create()
        .method(HttpMethod.POST)
        .uri(commonServicesUrl + "/kafka-rest-services")
        .body(payload, InventoryKafkaPayload.class)
        .retrieve()
        .bodyToMono(InventoryKafkaPayload.class);
  }

}
