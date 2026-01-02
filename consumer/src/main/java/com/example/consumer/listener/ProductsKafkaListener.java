package com.example.consumer.listener;

import com.example.consumer.dto.DebeziumEnvelope;
import com.example.consumer.service.DocumentAggregatorService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@Slf4j
@RequiredArgsConstructor
public class ProductsKafkaListener {

    private final DocumentAggregatorService aggregatorService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "dbserver1.public.products", groupId = "cdc-consumer-group")
    public void consume(String message) {
        try {
            log.debug("Received products message: {}", message);

            DebeziumEnvelope envelope = objectMapper.readValue(message, DebeziumEnvelope.class);
            String operation = envelope.getOp();

            if ("d".equals(operation)) {
                JsonNode before = envelope.getBefore();
                Long productId = before.get("id").asLong();
                aggregatorService.handleProductEvent(productId, null, null, operation);
            } else {
                JsonNode after = envelope.getAfter();
                Long productId = after.get("id").asLong();
                String name = after.get("name").asText();
                BigDecimal price = new BigDecimal(after.get("price").asText());

                aggregatorService.handleProductEvent(productId, name, price, operation);
            }
        } catch (Exception e) {
            log.error("Error processing products message", e);
        }
    }
}
