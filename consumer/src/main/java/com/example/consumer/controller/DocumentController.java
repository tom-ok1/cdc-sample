package com.example.consumer.controller;

import com.example.consumer.dto.OrderDocument;
import com.example.consumer.service.DocumentStoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentStoreService documentStoreService;

    @GetMapping("/documents")
    public ResponseEntity<List<OrderDocument>> getAllDocuments() {
        List<OrderDocument> documents = documentStoreService.findAll();
        return ResponseEntity.ok(documents);
    }

    @GetMapping("/documents/{orderId}")
    public ResponseEntity<OrderDocument> getDocument(@PathVariable Long orderId) {
        return documentStoreService.findById(orderId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/documents/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        Map<String, Object> stats = documentStoreService.getStats();
        return ResponseEntity.ok(stats);
    }
}
