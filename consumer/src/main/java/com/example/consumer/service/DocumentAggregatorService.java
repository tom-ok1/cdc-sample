package com.example.consumer.service;

import com.example.consumer.dto.OrderDocument;
import com.example.consumer.dto.OrderItemDto;
import com.example.consumer.dto.ProductData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class DocumentAggregatorService {

    private final DocumentStoreService documentStoreService;

    private final Map<Long, ProductData> productCache = new ConcurrentHashMap<>();
    private final Map<Long, OrderDocument> pendingDocuments = new ConcurrentHashMap<>();
    private final Map<Long, Set<Long>> pendingOrderItems = new ConcurrentHashMap<>();

    public void handleOrderEvent(Long orderId, Long userId, String status,
                                  BigDecimal totalPrice, LocalDateTime orderedAt, String operation) {
        log.info("Handling order event: orderId={}, operation={}", orderId, operation);

        if ("d".equals(operation)) {
            documentStoreService.delete(orderId);
            pendingDocuments.remove(orderId);
            pendingOrderItems.remove(orderId);
            return;
        }

        OrderDocument document = pendingDocuments.computeIfAbsent(orderId, id -> new OrderDocument());
        document.setOrderId(orderId);
        document.setUserId(userId);
        document.setStatus(status);
        document.setTotalPrice(totalPrice);
        document.setOrderedAt(orderedAt);

        checkAndFinalize(orderId);
    }

    public void handleOrderItemEvent(Long orderItemId, Long orderId, Long productId,
                                       Integer quantity, BigDecimal unitPrice, String operation) {
        log.info("Handling order item event: orderId={}, productId={}, operation={}",
                 orderId, productId, operation);

        if ("d".equals(operation)) {
            return;
        }

        OrderDocument document = pendingDocuments.computeIfAbsent(orderId, id -> new OrderDocument());

        ProductData product = productCache.get(productId);
        String productName = product != null ? product.getName() : "Unknown";

        OrderItemDto item = new OrderItemDto();
        item.setProductId(productId);
        item.setName(productName);
        item.setUnitPrice(unitPrice);
        item.setQty(quantity);

        boolean itemExists = document.getItems().stream()
                .anyMatch(i -> i.getProductId().equals(productId));

        if (!itemExists) {
            document.getItems().add(item);
        }

        pendingOrderItems.computeIfAbsent(orderId, k -> new HashSet<>()).add(orderItemId);

        checkAndFinalize(orderId);
    }

    public void handleProductEvent(Long productId, String name, BigDecimal price, String operation) {
        log.info("Handling product event: productId={}, name={}, operation={}",
                 productId, name, operation);

        if ("d".equals(operation)) {
            productCache.remove(productId);
            return;
        }

        ProductData product = new ProductData(productId, name, price);
        productCache.put(productId, product);

        updateDocumentsWithProduct(productId, name);
    }

    private void updateDocumentsWithProduct(Long productId, String name) {
        pendingDocuments.values().forEach(doc ->
            doc.getItems().stream()
                .filter(item -> item.getProductId().equals(productId))
                .forEach(item -> item.setName(name))
        );

        documentStoreService.findAll().forEach(doc ->
            doc.getItems().stream()
                .filter(item -> item.getProductId().equals(productId))
                .forEach(item -> {
                    item.setName(name);
                    documentStoreService.save(doc);
                })
        );
    }

    private void checkAndFinalize(Long orderId) {
        OrderDocument document = pendingDocuments.get(orderId);

        if (document == null || document.getOrderId() == null) {
            return;
        }

        if (document.getUserId() != null && !document.getItems().isEmpty()) {
            log.info("Finalizing document for order {}", orderId);
            documentStoreService.save(document);
        }
    }

    public Map<Long, ProductData> getProductCache() {
        return new HashMap<>(productCache);
    }
}
