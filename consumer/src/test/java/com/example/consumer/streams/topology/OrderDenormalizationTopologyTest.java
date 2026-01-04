package com.example.consumer.streams.topology;

import com.example.consumer.dto.OrderDocument;
import com.example.consumer.service.DocumentStoreService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderDenormalizationTopologyTest {

    private TopologyTestDriver testDriver;
    private TestInputTopic<String, String> ordersTopic;
    private TestInputTopic<String, String> orderItemsTopic;
    private TestInputTopic<String, String> productsTopic;
    private DocumentStoreService mockDocumentStoreService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        // Create ObjectMapper with JavaTimeModule
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        // Create mock DocumentStoreService
        mockDocumentStoreService = mock(DocumentStoreService.class);

        // Build the topology
        StreamsBuilder streamsBuilder = new StreamsBuilder();
        OrderDenormalizationTopology topology = new OrderDenormalizationTopology(
                mockDocumentStoreService,
                objectMapper
        );
        topology.buildTopology(streamsBuilder);

        // Create test driver
        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "test-app");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:1234");
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass());

        testDriver = new TopologyTestDriver(streamsBuilder.build(), props);

        // Create input topics
        ordersTopic = testDriver.createInputTopic(
                "dbserver1.public.orders",
                Serdes.String().serializer(),
                Serdes.String().serializer()
        );

        orderItemsTopic = testDriver.createInputTopic(
                "dbserver1.public.order_items",
                Serdes.String().serializer(),
                Serdes.String().serializer()
        );

        productsTopic = testDriver.createInputTopic(
                "dbserver1.public.products",
                Serdes.String().serializer(),
                Serdes.String().serializer()
        );
    }

    @AfterEach
    void tearDown() {
        if (testDriver != null) {
            testDriver.close();
        }
    }

    @Test
    void shouldCreateDenormalizedOrderDocumentWithProductEnrichment() throws Exception {
        // Given: Product, Order, and OrderItem events
        String productEvent = """
                {
                  "before": null,
                  "after": {
                    "id": 1,
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

        String orderEvent = """
                {
                  "before": null,
                  "after": {
                    "id": 100,
                    "user_id": 200,
                    "status": "PENDING",
                    "total_price": "999.99",
                    "ordered_at": 1640000000000,
                    "updated_at": 1640000000000
                  },
                  "op": "c",
                  "ts_ms": 1640000000000
                }
                """;

        String orderItemEvent = """
                {
                  "before": null,
                  "after": {
                    "id": 1,
                    "order_id": 100,
                    "product_id": 1,
                    "quantity": 1,
                    "unit_price": "999.99",
                    "created_at": 1640000000000
                  },
                  "op": "c",
                  "ts_ms": 1640000000000
                }
                """;

        // When: Send events to topics (product first for enrichment)
        productsTopic.pipeInput("1", productEvent);
        ordersTopic.pipeInput("100", orderEvent);
        orderItemsTopic.pipeInput("1", orderItemEvent);

        // Wait for processing
        Thread.sleep(100);

        // Then: DocumentStoreService should be called with denormalized document
        ArgumentCaptor<OrderDocument> documentCaptor = ArgumentCaptor.forClass(OrderDocument.class);
        verify(mockDocumentStoreService, timeout(1000).atLeastOnce()).save(documentCaptor.capture());

        OrderDocument savedDocument = documentCaptor.getValue();
        assertNotNull(savedDocument);
        assertEquals(100L, savedDocument.getOrderId());
        assertEquals(200L, savedDocument.getUserId());
        assertEquals("PENDING", savedDocument.getStatus());
        assertEquals(new BigDecimal("999.99"), savedDocument.getTotalPrice());

        // Verify items are enriched with product name
        assertNotNull(savedDocument.getItems());
        assertEquals(1, savedDocument.getItems().size());
        assertEquals("Laptop", savedDocument.getItems().get(0).getName());
        assertEquals(1L, savedDocument.getItems().get(0).getProductId());

        // Verify product summary
        assertNotNull(savedDocument.getProductSummary());
        assertEquals(1, savedDocument.getProductSummary().getUniqueProductCount());
        assertTrue(savedDocument.getProductSummary().getProductIds().contains(1L));
        assertEquals(1, savedDocument.getProductSummary().getTotalQuantity());
    }

    @Test
    void shouldHandleMultipleOrderItems() throws Exception {
        // Given: One order with multiple items from different products
        String product1Event = """
                {
                  "after": {
                    "id": 10,
                    "name": "Laptop",
                    "price": "999.99"
                  },
                  "op": "c"
                }
                """;

        String product2Event = """
                {
                  "after": {
                    "id": 20,
                    "name": "Mouse",
                    "price": "29.99"
                  },
                  "op": "c"
                }
                """;

        String orderEvent = """
                {
                  "after": {
                    "id": 101,
                    "user_id": 201,
                    "status": "COMPLETED",
                    "total_price": "1059.97",
                    "ordered_at": 1640000000000,
                    "updated_at": 1640000000000
                  },
                  "op": "c"
                }
                """;

        String orderItem1Event = """
                {
                  "after": {
                    "id": 10,
                    "order_id": 101,
                    "product_id": 10,
                    "quantity": 1,
                    "unit_price": "999.99",
                    "created_at": 1640000000000
                  },
                  "op": "c"
                }
                """;

        String orderItem2Event = """
                {
                  "after": {
                    "id": 11,
                    "order_id": 101,
                    "product_id": 20,
                    "quantity": 2,
                    "unit_price": "29.99",
                    "created_at": 1640000000000
                  },
                  "op": "c"
                }
                """;

        // When: Send events
        productsTopic.pipeInput("10", product1Event);
        productsTopic.pipeInput("20", product2Event);
        ordersTopic.pipeInput("101", orderEvent);
        orderItemsTopic.pipeInput("10", orderItem1Event);
        orderItemsTopic.pipeInput("11", orderItem2Event);

        Thread.sleep(100);

        // Then: Document should have both items with product names
        ArgumentCaptor<OrderDocument> documentCaptor = ArgumentCaptor.forClass(OrderDocument.class);
        verify(mockDocumentStoreService, timeout(1000).atLeastOnce()).save(documentCaptor.capture());

        OrderDocument savedDocument = documentCaptor.getValue();
        assertNotNull(savedDocument);
        assertEquals(101L, savedDocument.getOrderId());

        // Verify both items are present
        assertEquals(2, savedDocument.getItems().size());

        // Verify product names are enriched
        boolean hasLaptop = savedDocument.getItems().stream()
                .anyMatch(item -> "Laptop".equals(item.getName()) && item.getProductId().equals(10L));
        boolean hasMouse = savedDocument.getItems().stream()
                .anyMatch(item -> "Mouse".equals(item.getName()) && item.getProductId().equals(20L));
        assertTrue(hasLaptop, "Should have Laptop item");
        assertTrue(hasMouse, "Should have Mouse item");

        // Verify product summary shows 2 unique products
        assertNotNull(savedDocument.getProductSummary());
        assertEquals(2, savedDocument.getProductSummary().getUniqueProductCount());
        assertTrue(savedDocument.getProductSummary().getProductIds().contains(10L));
        assertTrue(savedDocument.getProductSummary().getProductIds().contains(20L));
        assertEquals(3, savedDocument.getProductSummary().getTotalQuantity()); // 1 + 2
    }

    @Test
    void shouldHandleMissingProduct() throws Exception {
        // Given: Order and OrderItem without corresponding Product
        String orderEvent = """
                {
                  "after": {
                    "id": 102,
                    "user_id": 202,
                    "status": "PENDING",
                    "total_price": "99.99",
                    "ordered_at": 1640000000000,
                    "updated_at": 1640000000000
                  },
                  "op": "c"
                }
                """;

        String orderItemEvent = """
                {
                  "after": {
                    "id": 20,
                    "order_id": 102,
                    "product_id": 999,
                    "quantity": 1,
                    "unit_price": "99.99",
                    "created_at": 1640000000000
                  },
                  "op": "c"
                }
                """;

        // When: Send events without product
        ordersTopic.pipeInput("102", orderEvent);
        orderItemsTopic.pipeInput("20", orderItemEvent);

        Thread.sleep(100);

        // Then: Document should be created with "Unknown" product name
        ArgumentCaptor<OrderDocument> documentCaptor = ArgumentCaptor.forClass(OrderDocument.class);
        verify(mockDocumentStoreService, timeout(1000).atLeastOnce()).save(documentCaptor.capture());

        OrderDocument savedDocument = documentCaptor.getValue();
        assertNotNull(savedDocument);
        assertEquals(102L, savedDocument.getOrderId());
        assertEquals(1, savedDocument.getItems().size());
        assertEquals("Unknown", savedDocument.getItems().get(0).getName());
        assertEquals(999L, savedDocument.getItems().get(0).getProductId());
    }

    @Test
    void shouldHandleOrderDeletion() throws Exception {
        // Given: Order delete event
        String deleteEvent = """
                {
                  "before": {
                    "id": 103,
                    "user_id": 203,
                    "status": "CANCELLED",
                    "total_price": "199.99"
                  },
                  "after": null,
                  "op": "d",
                  "ts_ms": 1640000000000
                }
                """;

        // When: Send delete event
        ordersTopic.pipeInput("103", deleteEvent);

        Thread.sleep(100);

        // Then: DocumentStoreService.delete should be called
        verify(mockDocumentStoreService, timeout(1000).atLeastOnce()).delete(103L);
    }

    @Test
    void shouldHandleProductUpdateAfterOrderItem() throws Exception {
        // Given: OrderItem arrives before Product (late-arriving product)
        String orderEvent = """
                {
                  "after": {
                    "id": 104,
                    "user_id": 204,
                    "status": "PENDING",
                    "total_price": "799.99",
                    "ordered_at": 1640000000000,
                    "updated_at": 1640000000000
                  },
                  "op": "c"
                }
                """;

        String orderItemEvent = """
                {
                  "after": {
                    "id": 30,
                    "order_id": 104,
                    "product_id": 30,
                    "quantity": 1,
                    "unit_price": "799.99",
                    "created_at": 1640000000000
                  },
                  "op": "c"
                }
                """;

        // When: Send order and item first (product name will be "Unknown")
        ordersTopic.pipeInput("104", orderEvent);
        orderItemsTopic.pipeInput("30", orderItemEvent);

        Thread.sleep(100);

        // Then: Product arrives late
        String productEvent = """
                {
                  "after": {
                    "id": 30,
                    "name": "Tablet",
                    "price": "799.99",
                    "description": "Premium tablet"
                  },
                  "op": "c"
                }
                """;

        productsTopic.pipeInput("30", productEvent);

        Thread.sleep(100);

        // Verify: Document should be updated with actual product name
        ArgumentCaptor<OrderDocument> documentCaptor = ArgumentCaptor.forClass(OrderDocument.class);
        verify(mockDocumentStoreService, timeout(1000).atLeast(2)).save(documentCaptor.capture());

        // Get the latest saved document
        OrderDocument latestDocument = documentCaptor.getAllValues().get(documentCaptor.getAllValues().size() - 1);
        assertNotNull(latestDocument);
        assertEquals(104L, latestDocument.getOrderId());

        // Verify product name is now "Tablet" instead of "Unknown"
        assertEquals(1, latestDocument.getItems().size());
        assertEquals("Tablet", latestDocument.getItems().get(0).getName());
    }

    @Test
    void shouldAggregateProductStatisticsCorrectly() throws Exception {
        // Given: Order with duplicate product (same product, different quantities)
        String productEvent = """
                {
                  "after": {
                    "id": 50,
                    "name": "Book",
                    "price": "19.99"
                  },
                  "op": "c"
                }
                """;

        String orderEvent = """
                {
                  "after": {
                    "id": 105,
                    "user_id": 205,
                    "status": "PENDING",
                    "total_price": "79.96",
                    "ordered_at": 1640000000000,
                    "updated_at": 1640000000000
                  },
                  "op": "c"
                }
                """;

        // Two order items for the same product (different line items)
        String orderItem1Event = """
                {
                  "after": {
                    "id": 40,
                    "order_id": 105,
                    "product_id": 50,
                    "quantity": 2,
                    "unit_price": "19.99",
                    "created_at": 1640000000000
                  },
                  "op": "c"
                }
                """;

        String orderItem2Event = """
                {
                  "after": {
                    "id": 41,
                    "order_id": 105,
                    "product_id": 50,
                    "quantity": 2,
                    "unit_price": "19.99",
                    "created_at": 1640000000000
                  },
                  "op": "c"
                }
                """;

        // When: Send events
        productsTopic.pipeInput("50", productEvent);
        ordersTopic.pipeInput("105", orderEvent);
        orderItemsTopic.pipeInput("40", orderItem1Event);
        orderItemsTopic.pipeInput("41", orderItem2Event);

        Thread.sleep(100);

        // Then: Product summary should show unique product count = 1, total quantity = 4
        ArgumentCaptor<OrderDocument> documentCaptor = ArgumentCaptor.forClass(OrderDocument.class);
        verify(mockDocumentStoreService, timeout(1000).atLeastOnce()).save(documentCaptor.capture());

        OrderDocument savedDocument = documentCaptor.getValue();
        assertNotNull(savedDocument);
        assertEquals(105L, savedDocument.getOrderId());
        assertEquals(2, savedDocument.getItems().size()); // 2 line items

        // Verify product summary
        assertNotNull(savedDocument.getProductSummary());
        assertEquals(1, savedDocument.getProductSummary().getUniqueProductCount()); // Only 1 unique product
        assertTrue(savedDocument.getProductSummary().getProductIds().contains(50L));
        assertEquals(4, savedDocument.getProductSummary().getTotalQuantity()); // 2 + 2 = 4
    }
}
