package com.example.producer.service;

import com.example.producer.dto.CreateOrderRequest;
import com.example.producer.dto.OrderItemRequest;
import com.example.producer.entity.Order;
import com.example.producer.entity.OrderItem;
import com.example.producer.entity.Product;
import com.example.producer.repository.OrderRepository;
import com.example.producer.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;

    @Transactional
    public Order createOrder(CreateOrderRequest request) {
        List<Long> productIds = request.getItems().stream()
                .map(OrderItemRequest::getProductId)
                .collect(Collectors.toList());

        Map<Long, Product> productMap = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        if (productMap.size() != productIds.size()) {
            throw new RuntimeException("Some products not found");
        }

        Order order = new Order();
        order.setUserId(request.getUserId());
        order.setStatus("PENDING");

        BigDecimal totalPrice = BigDecimal.ZERO;

        for (OrderItemRequest itemRequest : request.getItems()) {
            Product product = productMap.get(itemRequest.getProductId());

            OrderItem orderItem = new OrderItem();
            orderItem.setProductId(product.getId());
            orderItem.setQuantity(itemRequest.getQuantity());
            orderItem.setUnitPrice(product.getPrice());

            order.addItem(orderItem);

            BigDecimal itemTotal = product.getPrice()
                    .multiply(BigDecimal.valueOf(itemRequest.getQuantity()));
            totalPrice = totalPrice.add(itemTotal);
        }

        order.setTotalPrice(totalPrice);

        return orderRepository.save(order);
    }

    @Transactional
    public Order updateOrderStatus(Long orderId, String status) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));
        order.setStatus(status);
        return orderRepository.save(order);
    }

    public Order getOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));
    }

    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }
}
