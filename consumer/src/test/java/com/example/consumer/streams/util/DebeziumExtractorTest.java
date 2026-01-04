package com.example.consumer.streams.util;

import com.example.consumer.streams.model.OrderData;
import com.example.consumer.streams.model.ProductData;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class DebeziumExtractorTest {

    @Test
    void shouldExtractAfterFieldForCreateOperation() {
        // Given: Debezium envelope with create operation
        String debeziumJson = """
                {
                  "before": null,
                  "after": {
                    "id": 123,
                    "name": "Laptop",
                    "price": "999.99",
                    "description": "High-performance laptop",
                    "created_at": 1640000000000,
                    "updated_at": 1640000000000
                  },
                  "op": "c",
                  "ts_ms": 1640000000000
                }
                """;

        // When: Extract after field
        ProductData result = DebeziumExtractor.extractAfter(debeziumJson, ProductData.class);

        // Then: Product data should be extracted
        assertNotNull(result);
        assertEquals(123L, result.getId());
        assertEquals("Laptop", result.getName());
        assertEquals(new BigDecimal("999.99"), result.getPrice());
        assertEquals("High-performance laptop", result.getDescription());
    }

    @Test
    void shouldExtractAfterFieldForUpdateOperation() {
        // Given: Debezium envelope with update operation
        String debeziumJson = """
                {
                  "before": {
                    "id": 456,
                    "user_id": 100,
                    "status": "PENDING",
                    "total_price": "199.99"
                  },
                  "after": {
                    "id": 456,
                    "user_id": 100,
                    "status": "COMPLETED",
                    "total_price": "199.99",
                    "ordered_at": 1640000000000,
                    "updated_at": 1640100000000
                  },
                  "op": "u",
                  "ts_ms": 1640100000000
                }
                """;

        // When: Extract after field
        OrderData result = DebeziumExtractor.extractAfter(debeziumJson, OrderData.class);

        // Then: Updated order data should be extracted
        assertNotNull(result);
        assertEquals(456L, result.getId());
        assertEquals(100L, result.getUserId());
        assertEquals("COMPLETED", result.getStatus());
        assertEquals(new BigDecimal("199.99"), result.getTotalPrice());
    }

    @Test
    void shouldReturnNullForDeleteOperation() {
        // Given: Debezium envelope with delete operation
        String debeziumJson = """
                {
                  "before": {
                    "id": 789,
                    "user_id": 200,
                    "status": "CANCELLED",
                    "total_price": "299.99"
                  },
                  "after": null,
                  "op": "d",
                  "ts_ms": 1640200000000
                }
                """;

        // When: Extract after field
        OrderData result = DebeziumExtractor.extractAfter(debeziumJson, OrderData.class);

        // Then: Should return null for deletes
        assertNull(result);
    }

    @Test
    void shouldReturnNullForNullInput() {
        // When: Extract with null input
        OrderData result = DebeziumExtractor.extractAfter(null, OrderData.class);

        // Then: Should return null
        assertNull(result);
    }

    @Test
    void shouldGetOperationType() {
        // Given: Different operation types
        String createJson = """
                {"before": null, "after": {"id": 1}, "op": "c", "ts_ms": 1640000000000}
                """;
        String updateJson = """
                {"before": {"id": 1}, "after": {"id": 1}, "op": "u", "ts_ms": 1640000000000}
                """;
        String deleteJson = """
                {"before": {"id": 1}, "after": null, "op": "d", "ts_ms": 1640000000000}
                """;

        // When/Then: Extract operation types
        assertEquals("c", DebeziumExtractor.getOperation(createJson));
        assertEquals("u", DebeziumExtractor.getOperation(updateJson));
        assertEquals("d", DebeziumExtractor.getOperation(deleteJson));
    }

    @Test
    void shouldExtractDeletedId() {
        // Given: Debezium delete event
        String deleteJson = """
                {
                  "before": {
                    "id": 999,
                    "user_id": 300,
                    "status": "CANCELLED"
                  },
                  "after": null,
                  "op": "d",
                  "ts_ms": 1640300000000
                }
                """;

        // When: Extract deleted ID
        Long deletedId = DebeziumExtractor.extractDeletedId(deleteJson);

        // Then: Should return the ID from before field
        assertNotNull(deletedId);
        assertEquals(999L, deletedId);
    }

    @Test
    void shouldReturnNullDeletedIdForNonDeleteOperation() {
        // Given: Non-delete operation
        String createJson = """
                {
                  "before": null,
                  "after": {"id": 888},
                  "op": "c",
                  "ts_ms": 1640000000000
                }
                """;

        // When: Extract deleted ID
        Long deletedId = DebeziumExtractor.extractDeletedId(createJson);

        // Then: Should return null
        assertNull(deletedId);
    }

    @Test
    void shouldHandleMissingAfterField() {
        // Given: Envelope without after field
        String debeziumJson = """
                {
                  "before": {"id": 1},
                  "op": "c",
                  "ts_ms": 1640000000000
                }
                """;

        // When: Extract after field
        OrderData result = DebeziumExtractor.extractAfter(debeziumJson, OrderData.class);

        // Then: Should return null
        assertNull(result);
    }

    @Test
    void shouldThrowExceptionForInvalidJson() {
        // Given: Invalid JSON
        String invalidJson = "not valid json";

        // When/Then: Should throw RuntimeException
        assertThrows(RuntimeException.class, () -> {
            DebeziumExtractor.extractAfter(invalidJson, OrderData.class);
        });
    }
}
