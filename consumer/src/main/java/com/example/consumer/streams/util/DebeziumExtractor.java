package com.example.consumer.streams.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DebeziumExtractor {

    private static final ObjectMapper mapper = new ObjectMapper();

    public static <T> T extractAfter(String debeziumJson, Class<T> targetClass) {
        try {
            if (debeziumJson == null) {
                return null;
            }

            JsonNode envelope = mapper.readTree(debeziumJson);
            String op = envelope.get("op").asText();

            // Handle delete operations (tombstone)
            if ("d".equals(op)) {
                log.debug("Detected delete operation, returning null");
                return null;
            }

            // Extract the "after" field for create/update operations
            JsonNode after = envelope.get("after");
            if (after == null || after.isNull()) {
                log.warn("No 'after' field found in Debezium envelope");
                return null;
            }

            return mapper.treeToValue(after, targetClass);
        } catch (Exception e) {
            log.error("Failed to extract Debezium after field", e);
            throw new RuntimeException("Failed to extract Debezium after field", e);
        }
    }

    public static String getOperation(String debeziumJson) {
        try {
            if (debeziumJson == null) {
                return null;
            }

            JsonNode envelope = mapper.readTree(debeziumJson);
            JsonNode opNode = envelope.get("op");

            return opNode != null ? opNode.asText() : null;
        } catch (Exception e) {
            log.error("Failed to extract operation type from Debezium envelope", e);
            return null;
        }
    }

    public static Long extractDeletedId(String debeziumJson) {
        try {
            if (debeziumJson == null) {
                return null;
            }

            JsonNode envelope = mapper.readTree(debeziumJson);
            String op = getOperation(debeziumJson);

            if (!"d".equals(op)) {
                return null;
            }

            // For deletes, the ID is in the "before" field
            JsonNode before = envelope.get("before");
            if (before == null || before.isNull()) {
                return null;
            }

            JsonNode idNode = before.get("id");
            return idNode != null ? idNode.asLong() : null;
        } catch (Exception e) {
            log.error("Failed to extract deleted ID from Debezium envelope", e);
            return null;
        }
    }
}
