package com.example.consumer.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderDocument {
    private Long orderId;
    private Long userId;
    private String status;
    private BigDecimal totalPrice;
    private List<OrderItemDto> items = new ArrayList<>();
    private LocalDateTime orderedAt;

    private ProductSummary productSummary;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductSummary {
        private Integer uniqueProductCount;
        private Set<Long> productIds = new HashSet<>();
        private Integer totalQuantity;
    }
}
