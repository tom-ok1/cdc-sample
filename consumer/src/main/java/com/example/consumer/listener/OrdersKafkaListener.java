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
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Component
@Slf4j
@RequiredArgsConstructor
public class OrdersKafkaListener {

    private final DocumentAggregatorService aggregatorService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "dbserver1.public.orders", groupId = "cdc-consumer-group")
    public void consume(String message) {
        try {
            log.debug("Received orders message: {}", message);

            DebeziumEnvelope envelope = objectMapper.readValue(message, DebeziumEnvelope.class);
            String operation = envelope.getOp();

            if ("d".equals(operation)) {
                JsonNode before = envelope.getBefore();
                Long orderId = before.get("id").asLong();
                aggregatorService.handleOrderEvent(orderId, null, null, null, null, operation);
            } else {
                JsonNode after = envelope.getAfter();
                Long orderId = after.get("id").asLong();
                Long userId = after.get("user_id").asLong();
                String status = after.get("status").asText();
                BigDecimal totalPrice = new BigDecimal(after.get("total_price").asText());

                LocalDateTime orderedAt = null;
                if (after.has("ordered_at") && !after.get("ordered_at").isNull()) {
                    long timestamp = after.get("ordered_at").asLong();
                    orderedAt = LocalDateTime.ofInstant(
                            Instant.ofEpochMilli(timestamp),
                            ZoneId.systemDefault()
                    );
                }

                aggregatorService.handleOrderEvent(orderId, userId, status, totalPrice, orderedAt, operation);
            }
        } catch (Exception e) {
            log.error("Error processing orders message", e);
        }
    }
}
