package com.example.consumer.streams.serde;

import com.example.consumer.streams.model.OrderData;
import com.example.consumer.streams.model.ProductData;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class JsonSerdeTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    void shouldSerializeAndDeserializeOrderData() {
        // Given: OrderData object
        OrderData orderData = new OrderData();
        orderData.setId(123L);
        orderData.setUserId(456L);
        orderData.setStatus("PENDING");
        orderData.setTotalPrice(new BigDecimal("299.99"));
        orderData.setOrderedAt(LocalDateTime.of(2024, 1, 1, 10, 0, 0));
        orderData.setUpdatedAt(LocalDateTime.of(2024, 1, 1, 10, 0, 0));

        Serde<OrderData> serde = SerdeFactory.createJsonSerde(OrderData.class, objectMapper);
        Serializer<OrderData> serializer = serde.serializer();
        Deserializer<OrderData> deserializer = serde.deserializer();

        // When: Serialize and deserialize
        byte[] serialized = serializer.serialize("test-topic", orderData);
        OrderData deserialized = deserializer.deserialize("test-topic", serialized);

        // Then: Should match original
        assertNotNull(deserialized);
        assertEquals(orderData.getId(), deserialized.getId());
        assertEquals(orderData.getUserId(), deserialized.getUserId());
        assertEquals(orderData.getStatus(), deserialized.getStatus());
        assertEquals(orderData.getTotalPrice(), deserialized.getTotalPrice());
        assertEquals(orderData.getOrderedAt(), deserialized.getOrderedAt());
        assertEquals(orderData.getUpdatedAt(), deserialized.getUpdatedAt());
    }

    @Test
    void shouldSerializeAndDeserializeProductData() {
        // Given: ProductData object
        ProductData productData = new ProductData();
        productData.setId(789L);
        productData.setName("Laptop");
        productData.setPrice(new BigDecimal("1299.99"));
        productData.setDescription("High-performance laptop");
        productData.setCreatedAt(LocalDateTime.of(2024, 1, 1, 9, 0, 0));
        productData.setUpdatedAt(LocalDateTime.of(2024, 1, 1, 9, 0, 0));

        Serde<ProductData> serde = SerdeFactory.createJsonSerde(ProductData.class, objectMapper);
        Serializer<ProductData> serializer = serde.serializer();
        Deserializer<ProductData> deserializer = serde.deserializer();

        // When: Serialize and deserialize
        byte[] serialized = serializer.serialize("test-topic", productData);
        ProductData deserialized = deserializer.deserialize("test-topic", serialized);

        // Then: Should match original
        assertNotNull(deserialized);
        assertEquals(productData.getId(), deserialized.getId());
        assertEquals(productData.getName(), deserialized.getName());
        assertEquals(productData.getPrice(), deserialized.getPrice());
        assertEquals(productData.getDescription(), deserialized.getDescription());
        assertEquals(productData.getCreatedAt(), deserialized.getCreatedAt());
        assertEquals(productData.getUpdatedAt(), deserialized.getUpdatedAt());
    }

    @Test
    void shouldHandleNullSerialization() {
        // Given: Null object
        Serde<OrderData> serde = SerdeFactory.createJsonSerde(OrderData.class, objectMapper);
        Serializer<OrderData> serializer = serde.serializer();

        // When: Serialize null
        byte[] result = serializer.serialize("test-topic", null);

        // Then: Should return null
        assertNull(result);
    }

    @Test
    void shouldHandleNullDeserialization() {
        // Given: Null bytes
        Serde<OrderData> serde = SerdeFactory.createJsonSerde(OrderData.class, objectMapper);
        Deserializer<OrderData> deserializer = serde.deserializer();

        // When: Deserialize null
        OrderData result = deserializer.deserialize("test-topic", null);

        // Then: Should return null
        assertNull(result);
    }

    @Test
    void shouldPreserveBigDecimalPrecision() {
        // Given: OrderData with precise BigDecimal
        OrderData orderData = new OrderData();
        orderData.setId(1L);
        orderData.setTotalPrice(new BigDecimal("123.456789"));

        Serde<OrderData> serde = SerdeFactory.createJsonSerde(OrderData.class, objectMapper);
        Serializer<OrderData> serializer = serde.serializer();
        Deserializer<OrderData> deserializer = serde.deserializer();

        // When: Serialize and deserialize
        byte[] serialized = serializer.serialize("test-topic", orderData);
        OrderData deserialized = deserializer.deserialize("test-topic", serialized);

        // Then: Should preserve precision
        assertNotNull(deserialized);
        assertEquals(new BigDecimal("123.456789"), deserialized.getTotalPrice());
    }

    @Test
    void shouldHandleLocalDateTimeSerialization() {
        // Given: OrderData with LocalDateTime
        LocalDateTime now = LocalDateTime.of(2024, 12, 25, 15, 30, 45);
        OrderData orderData = new OrderData();
        orderData.setId(1L);
        orderData.setOrderedAt(now);

        Serde<OrderData> serde = SerdeFactory.createJsonSerde(OrderData.class, objectMapper);
        Serializer<OrderData> serializer = serde.serializer();
        Deserializer<OrderData> deserializer = serde.deserializer();

        // When: Serialize and deserialize
        byte[] serialized = serializer.serialize("test-topic", orderData);
        OrderData deserialized = deserializer.deserialize("test-topic", serialized);

        // Then: Should preserve LocalDateTime
        assertNotNull(deserialized);
        assertEquals(now, deserialized.getOrderedAt());
    }

    @Test
    void shouldIgnoreUnknownProperties() {
        // Given: JSON with extra unknown fields
        String jsonWithUnknownFields = """
                {
                  "id": 999,
                  "name": "Product",
                  "price": "99.99",
                  "unknownField": "should be ignored",
                  "anotherUnknown": 12345
                }
                """;

        Serde<ProductData> serde = SerdeFactory.createJsonSerde(ProductData.class, objectMapper);
        Deserializer<ProductData> deserializer = serde.deserializer();

        // When: Deserialize JSON with unknown fields
        ProductData result = deserializer.deserialize("test-topic", jsonWithUnknownFields.getBytes());

        // Then: Should deserialize successfully, ignoring unknown fields
        assertNotNull(result);
        assertEquals(999L, result.getId());
        assertEquals("Product", result.getName());
        assertEquals(new BigDecimal("99.99"), result.getPrice());
    }

    @Test
    void shouldRoundTripComplexObject() {
        // Given: Complex OrderData with all fields populated
        OrderData orderData = new OrderData(
                100L,
                200L,
                "COMPLETED",
                new BigDecimal("1599.99"),
                LocalDateTime.of(2024, 1, 15, 10, 30, 0),
                LocalDateTime.of(2024, 1, 15, 11, 0, 0)
        );

        Serde<OrderData> serde = SerdeFactory.createJsonSerde(OrderData.class, objectMapper);
        Serializer<OrderData> serializer = serde.serializer();
        Deserializer<OrderData> deserializer = serde.deserializer();

        // When: Multiple round trips
        byte[] serialized1 = serializer.serialize("test-topic", orderData);
        OrderData deserialized1 = deserializer.deserialize("test-topic", serialized1);
        byte[] serialized2 = serializer.serialize("test-topic", deserialized1);
        OrderData deserialized2 = deserializer.deserialize("test-topic", serialized2);

        // Then: Should be identical after multiple round trips
        assertEquals(orderData.getId(), deserialized2.getId());
        assertEquals(orderData.getUserId(), deserialized2.getUserId());
        assertEquals(orderData.getStatus(), deserialized2.getStatus());
        assertEquals(orderData.getTotalPrice(), deserialized2.getTotalPrice());
        assertEquals(orderData.getOrderedAt(), deserialized2.getOrderedAt());
        assertEquals(orderData.getUpdatedAt(), deserialized2.getUpdatedAt());
    }
}
