package com.example.consumer.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class OrderDocumentTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    void shouldSerializeAndDeserializeWithProductSummary() throws Exception {
        // Given: OrderDocument with ProductSummary
        OrderDocument document = new OrderDocument();
        document.setOrderId(123L);
        document.setUserId(456L);
        document.setStatus("COMPLETED");
        document.setTotalPrice(new BigDecimal("299.99"));
        document.setOrderedAt(LocalDateTime.of(2024, 1, 1, 10, 0, 0));

        // Add items
        List<OrderItemDto> items = new ArrayList<>();
        OrderItemDto item1 = new OrderItemDto();
        item1.setProductId(1L);
        item1.setName("Laptop");
        item1.setQty(1);
        item1.setUnitPrice(new BigDecimal("199.99"));
        items.add(item1);

        OrderItemDto item2 = new OrderItemDto();
        item2.setProductId(2L);
        item2.setName("Mouse");
        item2.setQty(2);
        item2.setUnitPrice(new BigDecimal("49.99"));
        items.add(item2);

        document.setItems(items);

        // Add product summary
        OrderDocument.ProductSummary summary = new OrderDocument.ProductSummary();
        summary.setUniqueProductCount(2);
        Set<Long> productIds = new HashSet<>();
        productIds.add(1L);
        productIds.add(2L);
        summary.setProductIds(productIds);
        summary.setTotalQuantity(3);
        document.setProductSummary(summary);

        // When: Serialize to JSON
        String json = objectMapper.writeValueAsString(document);

        // Then: Deserialize back
        OrderDocument deserialized = objectMapper.readValue(json, OrderDocument.class);

        // Verify all fields
        assertEquals(document.getOrderId(), deserialized.getOrderId());
        assertEquals(document.getUserId(), deserialized.getUserId());
        assertEquals(document.getStatus(), deserialized.getStatus());
        assertEquals(document.getTotalPrice(), deserialized.getTotalPrice());
        assertEquals(document.getOrderedAt(), deserialized.getOrderedAt());

        // Verify items
        assertEquals(2, deserialized.getItems().size());
        assertEquals("Laptop", deserialized.getItems().get(0).getName());
        assertEquals("Mouse", deserialized.getItems().get(1).getName());

        // Verify product summary
        assertNotNull(deserialized.getProductSummary());
        assertEquals(2, deserialized.getProductSummary().getUniqueProductCount());
        assertEquals(3, deserialized.getProductSummary().getTotalQuantity());
        assertTrue(deserialized.getProductSummary().getProductIds().contains(1L));
        assertTrue(deserialized.getProductSummary().getProductIds().contains(2L));
    }

    @Test
    void shouldHandleNullProductSummary() throws Exception {
        // Given: OrderDocument without ProductSummary
        OrderDocument document = new OrderDocument();
        document.setOrderId(789L);
        document.setUserId(101L);
        document.setStatus("PENDING");
        document.setTotalPrice(new BigDecimal("99.99"));

        // When: Serialize and deserialize
        String json = objectMapper.writeValueAsString(document);
        OrderDocument deserialized = objectMapper.readValue(json, OrderDocument.class);

        // Then: Should handle null ProductSummary gracefully
        assertEquals(document.getOrderId(), deserialized.getOrderId());
        assertNull(deserialized.getProductSummary());
    }

    @Test
    void shouldHandleEmptyProductSummary() throws Exception {
        // Given: OrderDocument with empty ProductSummary
        OrderDocument document = new OrderDocument();
        document.setOrderId(100L);

        OrderDocument.ProductSummary summary = new OrderDocument.ProductSummary();
        summary.setUniqueProductCount(0);
        summary.setProductIds(new HashSet<>());
        summary.setTotalQuantity(0);
        document.setProductSummary(summary);

        // When: Serialize and deserialize
        String json = objectMapper.writeValueAsString(document);
        OrderDocument deserialized = objectMapper.readValue(json, OrderDocument.class);

        // Then: Should preserve empty summary
        assertNotNull(deserialized.getProductSummary());
        assertEquals(0, deserialized.getProductSummary().getUniqueProductCount());
        assertEquals(0, deserialized.getProductSummary().getTotalQuantity());
        assertTrue(deserialized.getProductSummary().getProductIds().isEmpty());
    }

    @Test
    void shouldCreateProductSummaryCorrectly() {
        // Given: ProductSummary with data
        OrderDocument.ProductSummary summary = new OrderDocument.ProductSummary();

        Set<Long> productIds = new HashSet<>();
        productIds.add(10L);
        productIds.add(20L);
        productIds.add(30L);

        summary.setUniqueProductCount(3);
        summary.setProductIds(productIds);
        summary.setTotalQuantity(10);

        // Then: All fields should be set correctly
        assertEquals(3, summary.getUniqueProductCount());
        assertEquals(3, summary.getProductIds().size());
        assertTrue(summary.getProductIds().contains(10L));
        assertTrue(summary.getProductIds().contains(20L));
        assertTrue(summary.getProductIds().contains(30L));
        assertEquals(10, summary.getTotalQuantity());
    }

    @Test
    void shouldHandleComplexOrderDocument() throws Exception {
        // Given: Complex order with multiple items and full product summary
        OrderDocument document = new OrderDocument(
                200L,
                300L,
                "SHIPPED",
                new BigDecimal("1599.99"),
                new ArrayList<>(),
                LocalDateTime.of(2024, 2, 15, 14, 30, 0),
                null
        );

        // Add multiple items
        List<OrderItemDto> items = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            OrderItemDto item = new OrderItemDto();
            item.setProductId((long) i);
            item.setName("Product " + i);
            item.setQty(i);
            item.setUnitPrice(new BigDecimal("99.99"));
            items.add(item);
        }
        document.setItems(items);

        // Add comprehensive product summary
        OrderDocument.ProductSummary summary = new OrderDocument.ProductSummary();
        summary.setUniqueProductCount(5);
        Set<Long> productIds = new HashSet<>();
        for (long i = 1; i <= 5; i++) {
            productIds.add(i);
        }
        summary.setProductIds(productIds);
        summary.setTotalQuantity(15); // 1+2+3+4+5
        document.setProductSummary(summary);

        // When: Serialize to JSON
        String json = objectMapper.writeValueAsString(document);

        // Then: Deserialize and verify structure integrity
        OrderDocument deserialized = objectMapper.readValue(json, OrderDocument.class);

        assertEquals(200L, deserialized.getOrderId());
        assertEquals(300L, deserialized.getUserId());
        assertEquals("SHIPPED", deserialized.getStatus());
        assertEquals(5, deserialized.getItems().size());
        assertNotNull(deserialized.getProductSummary());
        assertEquals(5, deserialized.getProductSummary().getUniqueProductCount());
        assertEquals(15, deserialized.getProductSummary().getTotalQuantity());
        assertEquals(5, deserialized.getProductSummary().getProductIds().size());
    }

    @Test
    void shouldPreserveProductIdsAsSet() {
        // Given: ProductSummary with duplicate product IDs (should be deduplicated by Set)
        OrderDocument.ProductSummary summary = new OrderDocument.ProductSummary();

        Set<Long> productIds = new HashSet<>();
        productIds.add(1L);
        productIds.add(1L); // duplicate
        productIds.add(2L);
        productIds.add(2L); // duplicate

        summary.setProductIds(productIds);

        // Then: Set should contain only unique values
        assertEquals(2, summary.getProductIds().size());
        assertTrue(summary.getProductIds().contains(1L));
        assertTrue(summary.getProductIds().contains(2L));
    }
}
