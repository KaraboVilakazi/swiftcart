package com.swiftcart.order.dto;

import com.swiftcart.order.domain.Order;
import com.swiftcart.order.domain.OrderItem;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
    Long                    id,
    String                  status,
    BigDecimal              totalAmount,
    String                  shippingAddress,
    List<OrderItemResponse> items,
    Instant                 createdAt
) {
    public record OrderItemResponse(
        Long       productId,
        String     productName,
        int        quantity,
        BigDecimal unitPrice,
        BigDecimal subtotal
    ) {
        static OrderItemResponse from(OrderItem item) {
            return new OrderItemResponse(
                item.getProduct().getId(),
                item.getProduct().getName(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getSubtotal()
            );
        }
    }

    public static OrderResponse from(Order order) {
        return new OrderResponse(
            order.getId(),
            order.getStatus().name(),
            order.getTotalAmount(),
            order.getShippingAddress(),
            order.getItems().stream().map(OrderItemResponse::from).toList(),
            order.getCreatedAt()
        );
    }
}
