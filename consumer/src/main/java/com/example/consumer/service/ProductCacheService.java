package com.example.consumer.service;

import com.example.consumer.dto.ProductData;
import com.example.consumer.entity.DocumentStoreEntity;
import com.example.consumer.repository.DocumentStoreRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProductCacheService {

    private final DocumentStoreRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void save(ProductData product) {
        try {
            String id = "product:" + product.getId();
            DocumentStoreEntity entity = new DocumentStoreEntity();
            entity.setId(id);
            entity.setData(product);

            repository.save(entity);
            log.info("Saved product {} to PostgreSQL cache", product.getId());
        } catch (Exception e) {
            log.error("Error saving product to PostgreSQL cache", e);
            throw new RuntimeException("Failed to save product", e);
        }
    }

    @Transactional(readOnly = true)
    public Optional<ProductData> findById(Long productId) {
        try {
            String id = "product:" + productId;
            return repository.findById(id)
                    .map(entity -> objectMapper.convertValue(entity.getData(), ProductData.class));
        } catch (Exception e) {
            log.error("Error finding product from PostgreSQL cache", e);
            return Optional.empty();
        }
    }

    @Transactional
    public void delete(Long productId) {
        try {
            String id = "product:" + productId;
            repository.deleteById(id);
            log.info("Deleted product {} from PostgreSQL cache", productId);
        } catch (Exception e) {
            log.error("Error deleting product from PostgreSQL cache", e);
        }
    }

    @Transactional(readOnly = true)
    public Map<Long, ProductData> findAll() {
        Map<Long, ProductData> productMap = new HashMap<>();
        try {
            repository.findAll().stream()
                    .filter(entity -> entity.getId().startsWith("product:"))
                    .forEach(entity -> {
                        ProductData product = objectMapper.convertValue(entity.getData(), ProductData.class);
                        productMap.put(product.getId(), product);
                    });
        } catch (Exception e) {
            log.error("Error finding all products from PostgreSQL cache", e);
        }
        return productMap;
    }
}
