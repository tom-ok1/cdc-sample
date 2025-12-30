package com.example.consumer.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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
}
