package com.example.consumer.streams.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderItemsAggregate {
    private List<EnrichedOrderItem> items = new ArrayList<>();
    private Set<Long> uniqueProductIds = new HashSet<>();
    private Integer totalQuantity = 0;

    public void addItem(EnrichedOrderItem item) {
        this.items.add(item);
        this.uniqueProductIds.add(item.getProductId());
        this.totalQuantity += item.getQuantity();
    }
}
