package com.example.consumer.streams.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class OrderItemsAggregateTest {

    @Test
    void shouldAddItemAndUpdateStatistics() {
        // Given: Empty aggregate
        OrderItemsAggregate aggregate = new OrderItemsAggregate();

        // When: Add an item
        EnrichedOrderItem item = new EnrichedOrderItem();
        item.setOrderItemId(1L);
        item.setOrderId(100L);
        item.setProductId(10L);
        item.setProductName("Laptop");
        item.setQuantity(2);
        item.setUnitPrice(new BigDecimal("999.99"));

        aggregate.addItem(item);

        // Then: Statistics should be updated
        assertEquals(1, aggregate.getItems().size());
        assertEquals(1, aggregate.getUniqueProductIds().size());
        assertTrue(aggregate.getUniqueProductIds().contains(10L));
        assertEquals(2, aggregate.getTotalQuantity());
    }

    @Test
    void shouldTrackMultipleItemsWithDifferentProducts() {
        // Given: Empty aggregate
        OrderItemsAggregate aggregate = new OrderItemsAggregate();

        // When: Add multiple items with different products
        EnrichedOrderItem item1 = new EnrichedOrderItem(1L, 100L, 10L, "Laptop", new BigDecimal("999.99"), 1, new BigDecimal("999.99"));
        EnrichedOrderItem item2 = new EnrichedOrderItem(2L, 100L, 20L, "Mouse", new BigDecimal("29.99"), 2, new BigDecimal("29.99"));
        EnrichedOrderItem item3 = new EnrichedOrderItem(3L, 100L, 30L, "Keyboard", new BigDecimal("79.99"), 1, new BigDecimal("79.99"));

        aggregate.addItem(item1);
        aggregate.addItem(item2);
        aggregate.addItem(item3);

        // Then: Should track all items and unique products
        assertEquals(3, aggregate.getItems().size());
        assertEquals(3, aggregate.getUniqueProductIds().size());
        assertTrue(aggregate.getUniqueProductIds().contains(10L));
        assertTrue(aggregate.getUniqueProductIds().contains(20L));
        assertTrue(aggregate.getUniqueProductIds().contains(30L));
        assertEquals(4, aggregate.getTotalQuantity()); // 1 + 2 + 1
    }

    @Test
    void shouldHandleDuplicateProducts() {
        // Given: Empty aggregate
        OrderItemsAggregate aggregate = new OrderItemsAggregate();

        // When: Add multiple items with same product (different line items)
        EnrichedOrderItem item1 = new EnrichedOrderItem(1L, 100L, 10L, "Book", new BigDecimal("19.99"), 2, new BigDecimal("19.99"));
        EnrichedOrderItem item2 = new EnrichedOrderItem(2L, 100L, 10L, "Book", new BigDecimal("19.99"), 3, new BigDecimal("19.99"));

        aggregate.addItem(item1);
        aggregate.addItem(item2);

        // Then: Should have 2 items but only 1 unique product
        assertEquals(2, aggregate.getItems().size());
        assertEquals(1, aggregate.getUniqueProductIds().size());
        assertTrue(aggregate.getUniqueProductIds().contains(10L));
        assertEquals(5, aggregate.getTotalQuantity()); // 2 + 3
    }

    @Test
    void shouldInitializeWithDefaultValues() {
        // Given/When: Create new aggregate
        OrderItemsAggregate aggregate = new OrderItemsAggregate();

        // Then: Should have empty collections and zero total
        assertNotNull(aggregate.getItems());
        assertTrue(aggregate.getItems().isEmpty());
        assertNotNull(aggregate.getUniqueProductIds());
        assertTrue(aggregate.getUniqueProductIds().isEmpty());
        assertEquals(0, aggregate.getTotalQuantity());
    }

    @Test
    void shouldAccumulateQuantitiesCorrectly() {
        // Given: Empty aggregate
        OrderItemsAggregate aggregate = new OrderItemsAggregate();

        // When: Add items with various quantities
        aggregate.addItem(new EnrichedOrderItem(1L, 100L, 10L, "Product1", null, 5, new BigDecimal("10.00")));
        aggregate.addItem(new EnrichedOrderItem(2L, 100L, 20L, "Product2", null, 10, new BigDecimal("20.00")));
        aggregate.addItem(new EnrichedOrderItem(3L, 100L, 30L, "Product3", null, 3, new BigDecimal("30.00")));
        aggregate.addItem(new EnrichedOrderItem(4L, 100L, 10L, "Product1", null, 7, new BigDecimal("10.00")));

        // Then: Total quantity should be sum of all quantities
        assertEquals(4, aggregate.getItems().size());
        assertEquals(3, aggregate.getUniqueProductIds().size());
        assertEquals(25, aggregate.getTotalQuantity()); // 5 + 10 + 3 + 7
    }

    @Test
    void shouldMaintainInsertionOrderForItems() {
        // Given: Empty aggregate
        OrderItemsAggregate aggregate = new OrderItemsAggregate();

        // When: Add items in specific order
        EnrichedOrderItem item1 = new EnrichedOrderItem(1L, 100L, 10L, "First", null, 1, new BigDecimal("10.00"));
        EnrichedOrderItem item2 = new EnrichedOrderItem(2L, 100L, 20L, "Second", null, 1, new BigDecimal("20.00"));
        EnrichedOrderItem item3 = new EnrichedOrderItem(3L, 100L, 30L, "Third", null, 1, new BigDecimal("30.00"));

        aggregate.addItem(item1);
        aggregate.addItem(item2);
        aggregate.addItem(item3);

        // Then: Items should be in insertion order
        assertEquals("First", aggregate.getItems().get(0).getProductName());
        assertEquals("Second", aggregate.getItems().get(1).getProductName());
        assertEquals("Third", aggregate.getItems().get(2).getProductName());
    }
}
