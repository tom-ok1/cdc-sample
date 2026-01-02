package com.example.consumer.service;

import com.example.consumer.dto.OrderDocument;
import com.example.consumer.entity.DocumentStoreEntity;
import com.example.consumer.repository.DocumentStoreRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class DocumentStoreService {

    private final DocumentStoreRepository repository;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Transactional
    public void save(OrderDocument document) {
        try {
            String id = "order:" + document.getOrderId();
            DocumentStoreEntity entity = new DocumentStoreEntity();
            entity.setId(id);
            entity.setData(document);

            repository.save(entity);
            log.info("Saved document for order {} to PostgreSQL", document.getOrderId());
        } catch (Exception e) {
            log.error("Error saving document to PostgreSQL", e);
            throw new RuntimeException("Failed to save document", e);
        }
    }

    @Transactional(readOnly = true)
    public Optional<OrderDocument> findById(Long orderId) {
        try {
            String id = "order:" + orderId;
            return repository.findById(id)
                    .map(entity -> objectMapper.convertValue(entity.getData(), OrderDocument.class));
        } catch (Exception e) {
            log.error("Error finding document from PostgreSQL", e);
            return Optional.empty();
        }
    }

    @Transactional(readOnly = true)
    public List<OrderDocument> findAll() {
        try {
            return repository.findAll().stream()
                    .filter(entity -> entity.getId().startsWith("order:"))
                    .map(entity -> objectMapper.convertValue(entity.getData(), OrderDocument.class))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error finding all documents from PostgreSQL", e);
            return new ArrayList<>();
        }
    }

    @Transactional
    public void delete(Long orderId) {
        try {
            String id = "order:" + orderId;
            repository.deleteById(id);
            log.info("Deleted document for order {} from PostgreSQL", orderId);
        } catch (Exception e) {
            log.error("Error deleting document from PostgreSQL", e);
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        long count = repository.findAll().stream()
                .filter(entity -> entity.getId().startsWith("order:"))
                .count();
        stats.put("totalDocuments", count);
        return stats;
    }
}
