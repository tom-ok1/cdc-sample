package com.example.consumer.service;

import com.example.consumer.dto.OrderDocument;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class DocumentStoreService {

    private final Map<Long, OrderDocument> documentStore = new ConcurrentHashMap<>();

    public void save(OrderDocument document) {
        documentStore.put(document.getOrderId(), document);
        log.info("Saved document for order {}", document.getOrderId());
    }

    public Optional<OrderDocument> findById(Long orderId) {
        return Optional.ofNullable(documentStore.get(orderId));
    }

    public List<OrderDocument> findAll() {
        return new ArrayList<>(documentStore.values());
    }

    public void delete(Long orderId) {
        documentStore.remove(orderId);
        log.info("Deleted document for order {}", orderId);
    }

    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalDocuments", documentStore.size());
        return stats;
    }
}
