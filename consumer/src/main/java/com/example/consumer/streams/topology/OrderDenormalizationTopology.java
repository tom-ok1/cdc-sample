package com.example.consumer.streams.topology;

import com.example.consumer.dto.OrderDocument;
import com.example.consumer.dto.OrderItemDto;
import com.example.consumer.service.DocumentStoreService;
import com.example.consumer.streams.model.*;
import com.example.consumer.streams.serde.SerdeFactory;
import com.example.consumer.streams.util.DebeziumExtractor;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.*;
import org.apache.kafka.streams.state.KeyValueStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@Slf4j
public class OrderDenormalizationTopology {

        private final DocumentStoreService documentStoreService;
        private final ObjectMapper objectMapper;

        public OrderDenormalizationTopology(
                        DocumentStoreService documentStoreService,
                        ObjectMapper objectMapper) {
                this.documentStoreService = documentStoreService;
                this.objectMapper = objectMapper;
        }

        @Autowired
        public void buildTopology(StreamsBuilder streamsBuilder) {
                log.info("Building Order Denormalization Topology");

                // Create Serdes for domain objects
                Serde<OrderData> orderSerde = SerdeFactory.createJsonSerde(OrderData.class, objectMapper);
                Serde<OrderItemData> orderItemSerde = SerdeFactory.createJsonSerde(OrderItemData.class, objectMapper);
                Serde<ProductData> productSerde = SerdeFactory.createJsonSerde(ProductData.class, objectMapper);
                Serde<EnrichedOrderItem> enrichedItemSerde = SerdeFactory.createJsonSerde(EnrichedOrderItem.class,
                                objectMapper);
                Serde<OrderItemsAggregate> aggregateSerde = SerdeFactory.createJsonSerde(OrderItemsAggregate.class,
                                objectMapper);
                Serde<OrderDocument> documentSerde = SerdeFactory.createJsonSerde(OrderDocument.class, objectMapper);

                // ===================================================================
                // STAGE 1: Extract Orders and create KTable
                // ===================================================================
                KTable<Long, OrderData> ordersTable = streamsBuilder
                                .stream("dbserver1.public.orders",
                                                Consumed.with(Serdes.String(), Serdes.String()))
                                .peek((k, v) -> log.debug("Received order event: key={}, value={}", k, v))
                                .mapValues(value -> DebeziumExtractor.extractAfter(value, OrderData.class))
                                .filter((key, value) -> value != null) // Filter out deletes
                                .selectKey((key, value) -> value.getId())
                                .repartition()
                                .peek((k, v) -> log.debug("Extracted order: orderId={}, userId={}", k, v.getUserId()))
                                .toTable(
                                                Materialized.<Long, OrderData, KeyValueStore<Bytes, byte[]>>as(
                                                                "orders-store")
                                                                .withKeySerde(Serdes.Long())
                                                                .withValueSerde(orderSerde));

                // ===================================================================
                // STAGE 2: Extract Products and create KTable
                // ===================================================================
                KTable<Long, ProductData> productsTable = streamsBuilder
                                .stream("dbserver1.public.products",
                                                Consumed.with(Serdes.String(), Serdes.String()))
                                .peek((k, v) -> log.debug("Received product event: key={}, value={}", k, v))
                                .mapValues(value -> DebeziumExtractor.extractAfter(value, ProductData.class))
                                .filter((key, value) -> value != null)
                                .selectKey((key, value) -> value.getId())
                                .repartition()
                                .peek((k, v) -> log.debug("Extracted product: productId={}, name={}", k, v.getName()))
                                .toTable(
                                                Materialized.<Long, ProductData, KeyValueStore<Bytes, byte[]>>as(
                                                                "products-store")
                                                                .withKeySerde(Serdes.Long())
                                                                .withValueSerde(productSerde));

                // ===================================================================
                // STAGE 3: Extract Order Items and REPARTITION BY PRODUCT_ID
                // ===================================================================
                KStream<Long, OrderItemData> orderItemsByProduct = streamsBuilder
                                .stream("dbserver1.public.order_items",
                                                Consumed.with(Serdes.String(), Serdes.String()))
                                .peek((k, v) -> log.debug("Received order_item event: key={}, value={}", k, v))
                                .mapValues(value -> DebeziumExtractor.extractAfter(value, OrderItemData.class))
                                .filter((key, value) -> value != null)
                                .selectKey((key, value) -> value.getProductId()) // REPARTITION BY PRODUCT_ID
                                .repartition()
                                .peek((k, v) -> log.debug("Repartitioned by productId: key={}, orderId={}", k,
                                                v.getOrderId()));

                // ===================================================================
                // STAGE 4: JOIN Order Items with Products (LEFT JOIN)
                // ===================================================================
                KStream<Long, EnrichedOrderItem> enrichedOrderItems = orderItemsByProduct
                                .leftJoin(
                                                productsTable,
                                                (orderItem, product) -> {
                                                        EnrichedOrderItem enriched = new EnrichedOrderItem();
                                                        enriched.setOrderItemId(orderItem.getId());
                                                        enriched.setOrderId(orderItem.getOrderId());
                                                        enriched.setProductId(orderItem.getProductId());
                                                        enriched.setQuantity(orderItem.getQuantity());
                                                        enriched.setUnitPrice(orderItem.getUnitPrice());

                                                        if (product != null) {
                                                                enriched.setProductName(product.getName());
                                                                enriched.setProductPrice(product.getPrice());
                                                                log.debug("Enriched order item with product: orderId={}, productId={}, productName={}",
                                                                                orderItem.getOrderId(),
                                                                                orderItem.getProductId(),
                                                                                product.getName());
                                                        } else {
                                                                enriched.setProductName("Unknown");
                                                                enriched.setProductPrice(null);
                                                                log.warn("Product not found for enrichment: productId={}",
                                                                                orderItem.getProductId());
                                                        }

                                                        return enriched;
                                                },
                                                Joined.with(Serdes.Long(), orderItemSerde, productSerde))
                                .peek((productId, enriched) -> log.debug(
                                                "Enriched order item: productId={}, orderId={}, productName={}",
                                                productId, enriched.getOrderId(), enriched.getProductName()));

                // ===================================================================
                // STAGE 5: REPARTITION BY ORDER_ID and AGGREGATE
                // ===================================================================
                KTable<Long, OrderItemsAggregate> orderItemsAggregated = enrichedOrderItems
                                .selectKey((productId, enriched) -> enriched.getOrderId()) // REPARTITION BY ORDER_ID
                                .peek((orderId, item) -> log.debug("Repartitioned by orderId: key={}, productId={}",
                                                orderId, item.getProductId()))
                                .groupByKey(Grouped.with(Serdes.Long(), enrichedItemSerde))
                                .aggregate(
                                                OrderItemsAggregate::new, // Initializer
                                                (orderId, enrichedItem, aggregate) -> {
                                                        aggregate.addItem(enrichedItem);
                                                        log.debug("Aggregating: orderId={}, productId={}, totalItems={}",
                                                                        orderId, enrichedItem.getProductId(),
                                                                        aggregate.getItems().size());
                                                        return aggregate;
                                                },
                                                Materialized.<Long, OrderItemsAggregate, KeyValueStore<Bytes, byte[]>>as(
                                                                "order-items-aggregated-store")
                                                                .withKeySerde(Serdes.Long())
                                                                .withValueSerde(aggregateSerde));

                // ===================================================================
                // STAGE 6: JOIN Aggregated Items with Orders
                // ===================================================================
                KTable<Long, OrderDocument> finalDocuments = orderItemsAggregated
                                .join(
                                                ordersTable,
                                                (itemsAggregate, order) -> {
                                                        OrderDocument document = new OrderDocument();
                                                        document.setOrderId(order.getId());
                                                        document.setUserId(order.getUserId());
                                                        document.setStatus(order.getStatus());
                                                        document.setTotalPrice(order.getTotalPrice());
                                                        document.setOrderedAt(order.getOrderedAt());

                                                        // Convert EnrichedOrderItem to OrderItemDto
                                                        List<OrderItemDto> items = itemsAggregate.getItems().stream()
                                                                        .map(enriched -> {
                                                                                OrderItemDto dto = new OrderItemDto();
                                                                                dto.setProductId(enriched
                                                                                                .getProductId());
                                                                                dto.setName(enriched.getProductName());
                                                                                dto.setUnitPrice(enriched
                                                                                                .getUnitPrice());
                                                                                dto.setQty(enriched.getQuantity());
                                                                                return dto;
                                                                        })
                                                                        .collect(Collectors.toList());
                                                        document.setItems(items);

                                                        // Add product-level aggregation
                                                        OrderDocument.ProductSummary summary = new OrderDocument.ProductSummary();
                                                        summary.setUniqueProductCount(
                                                                        itemsAggregate.getUniqueProductIds().size());
                                                        summary.setProductIds(itemsAggregate.getUniqueProductIds());
                                                        summary.setTotalQuantity(itemsAggregate.getTotalQuantity());
                                                        document.setProductSummary(summary);

                                                        log.info("Created final document: orderId={}, itemCount={}, uniqueProducts={}",
                                                                        document.getOrderId(), items.size(),
                                                                        summary.getUniqueProductCount());

                                                        return document;
                                                },
                                                Materialized.<Long, OrderDocument, KeyValueStore<Bytes, byte[]>>as(
                                                                "final-documents-store")
                                                                .withKeySerde(Serdes.Long())
                                                                .withValueSerde(documentSerde));

                // ===================================================================
                // STAGE 7: SINK to PostgreSQL
                // ===================================================================
                finalDocuments
                                .toStream()
                                .foreach((orderId, orderDoc) -> {
                                        try {
                                                log.info("Saving document to PostgreSQL: orderId={}", orderId);
                                                documentStoreService.save(orderDoc);
                                        } catch (Exception e) {
                                                log.error("Failed to save document: orderId={}", orderId, e);
                                                // In production: send to DLQ (Dead Letter Queue)
                                        }
                                });

                // ===================================================================
                // HANDLE DELETES (separate stream)
                // ===================================================================
                streamsBuilder
                                .stream("dbserver1.public.orders",
                                                Consumed.with(Serdes.String(), Serdes.String()))
                                .filter((key, value) -> {
                                        String op = DebeziumExtractor.getOperation(value);
                                        return "d".equals(op);
                                })
                                .mapValues(value -> DebeziumExtractor.extractDeletedId(value))
                                .filter((key, orderId) -> orderId != null)
                                .foreach((key, orderId) -> {
                                        log.info("Deleting document from PostgreSQL: orderId={}", orderId);
                                        try {
                                                documentStoreService.delete(orderId);
                                        } catch (Exception e) {
                                                log.error("Failed to delete document: orderId={}", orderId, e);
                                        }
                                });

                log.info("Order Denormalization Topology built successfully");
        }
}
