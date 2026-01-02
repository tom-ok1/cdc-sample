package com.example.consumer.service;

import com.example.consumer.dto.OrderDocument;
import com.example.consumer.dto.OrderItemDto;
import com.example.consumer.dto.ProductData;
import com.example.consumer.entity.DocumentStoreEntity;
import com.example.consumer.repository.DocumentStoreRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class DocumentAggregatorService {

    private final DocumentStoreService documentStoreService;
    private final ProductCacheService productCacheService;
    private final DocumentStoreRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void handleOrderEvent(Long orderId, Long userId, String status,
                                  BigDecimal totalPrice, LocalDateTime orderedAt, String operation) {
        log.info("Handling order event: orderId={}, operation={}", orderId, operation);

        if ("d".equals(operation)) {
            documentStoreService.delete(orderId);
            deletePendingDocument(orderId);
            deletePendingOrderItems(orderId);
            return;
        }

        OrderDocument document = getPendingDocument(orderId);
        document.setOrderId(orderId);
        document.setUserId(userId);
        document.setStatus(status);
        document.setTotalPrice(totalPrice);
        document.setOrderedAt(orderedAt);
        savePendingDocument(orderId, document);

        checkAndFinalize(orderId);
    }

    @Transactional
    public void handleOrderItemEvent(Long orderItemId, Long orderId, Long productId,
                                       Integer quantity, BigDecimal unitPrice, String operation) {
        log.info("Handling order item event: orderId={}, productId={}, operation={}",
                 orderId, productId, operation);

        if ("d".equals(operation)) {
            return;
        }

        OrderDocument document = getPendingDocument(orderId);

        Optional<ProductData> productOpt = productCacheService.findById(productId);
        String productName = productOpt.map(ProductData::getName).orElse("Unknown");

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

        savePendingDocument(orderId, document);
        addPendingOrderItem(orderId, orderItemId);

        checkAndFinalize(orderId);
    }

    @Transactional
    public void handleProductEvent(Long productId, String name, BigDecimal price, String operation) {
        log.info("Handling product event: productId={}, name={}, operation={}",
                 productId, name, operation);

        if ("d".equals(operation)) {
            productCacheService.delete(productId);
            return;
        }

        ProductData product = new ProductData(productId, name, price);
        productCacheService.save(product);

        updateDocumentsWithProduct(productId, name);
    }

    private void updateDocumentsWithProduct(Long productId, String name) {
        // Update pending documents
        repository.findAll().stream()
                .filter(entity -> entity.getId().startsWith("pending:"))
                .forEach(entity -> {
                    OrderDocument doc = objectMapper.convertValue(entity.getData(), OrderDocument.class);
                    boolean updated = false;
                    for (OrderItemDto item : doc.getItems()) {
                        if (item.getProductId().equals(productId)) {
                            item.setName(name);
                            updated = true;
                        }
                    }
                    if (updated) {
                        entity.setData(doc);
                        repository.save(entity);
                    }
                });

        // Update finalized documents
        documentStoreService.findAll().forEach(doc -> {
            boolean updated = false;
            for (OrderItemDto item : doc.getItems()) {
                if (item.getProductId().equals(productId)) {
                    item.setName(name);
                    updated = true;
                }
            }
            if (updated) {
                documentStoreService.save(doc);
            }
        });
    }

    private void checkAndFinalize(Long orderId) {
        OrderDocument document = getPendingDocument(orderId);

        if (document == null || document.getOrderId() == null) {
            return;
        }

        if (document.getUserId() != null && !document.getItems().isEmpty()) {
            log.info("Finalizing document for order {}", orderId);
            documentStoreService.save(document);
        }
    }

    private OrderDocument getPendingDocument(Long orderId) {
        String id = "pending:" + orderId;
        return repository.findById(id)
                .map(entity -> objectMapper.convertValue(entity.getData(), OrderDocument.class))
                .orElse(new OrderDocument());
    }

    private void savePendingDocument(Long orderId, OrderDocument document) {
        String id = "pending:" + orderId;
        DocumentStoreEntity entity = repository.findById(id).orElse(new DocumentStoreEntity());
        entity.setId(id);
        entity.setData(document);
        repository.save(entity);
    }

    private void deletePendingDocument(Long orderId) {
        String id = "pending:" + orderId;
        repository.deleteById(id);
    }

    @SuppressWarnings("unchecked")
    private void addPendingOrderItem(Long orderId, Long orderItemId) {
        String id = "pending_items:" + orderId;
        Set<Long> items = repository.findById(id)
                .map(entity -> {
                    Object data = entity.getData();
                    if (data instanceof List) {
                        return new HashSet<>((List<Long>) data);
                    }
                    return new HashSet<Long>();
                })
                .orElse(new HashSet<>());

        items.add(orderItemId);

        DocumentStoreEntity entity = repository.findById(id).orElse(new DocumentStoreEntity());
        entity.setId(id);
        entity.setData(new ArrayList<>(items));
        repository.save(entity);
    }

    private void deletePendingOrderItems(Long orderId) {
        String id = "pending_items:" + orderId;
        repository.deleteById(id);
    }

    public Map<Long, ProductData> getProductCache() {
        return productCacheService.findAll();
    }
}
