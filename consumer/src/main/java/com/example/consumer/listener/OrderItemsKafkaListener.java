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
public class OrderItemsKafkaListener {

    private final DocumentAggregatorService aggregatorService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "dbserver1.public.order_items", groupId = "cdc-consumer-group")
    public void consume(String message) {
        try {
            log.debug("Received order_items message: {}", message);

            DebeziumEnvelope envelope = objectMapper.readValue(message, DebeziumEnvelope.class);
            String operation = envelope.getOp();

            if (!"d".equals(operation)) {
                JsonNode after = envelope.getAfter();
                Long orderItemId = after.get("id").asLong();
                Long orderId = after.get("order_id").asLong();
                Long productId = after.get("product_id").asLong();
                Integer quantity = after.get("quantity").asInt();
                BigDecimal unitPrice = new BigDecimal(after.get("unit_price").asText());

                aggregatorService.handleOrderItemEvent(
                        orderItemId, orderId, productId, quantity, unitPrice, operation
                );
            }
        } catch (Exception e) {
            log.error("Error processing order_items message", e);
        }
    }
}
