package com.swiftcart.order.event;

import com.swiftcart.order.domain.Order;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.List;

/**
 * Domain event published when an order is successfully placed.
 *
 * Event-driven design rationale:
 * --------------------------------
 * Using Spring's ApplicationEventPublisher decouples the OrderService
 * from downstream concerns (inventory confirmation, notifications).
 *
 * Benefits:
 *  - OrderService doesn't need to know about NotificationService.
 *  - Adding a new reaction (e.g. analytics, loyalty points) = one new
 *    @EventListener class, zero changes to OrderService.
 *  - Can be swapped to an async broker (Kafka, RabbitMQ) by changing
 *    the publisher implementation without touching business logic.
 *
 * Current impl: synchronous in-process events via Spring ApplicationContext.
 * For production, annotate listeners with @Async and configure a thread pool,
 * or swap to a message broker for durability + fan-out.
 */
@Getter
public class OrderCreatedEvent extends ApplicationEvent {

    private final Long   orderId;
    private final Long   userId;
    private final String userEmail;
    private final List<OrderItemDetail> items;

    public OrderCreatedEvent(Object source, Order order) {
        super(source);
        this.orderId   = order.getId();
        this.userId    = order.getUser().getId();
        this.userEmail = order.getUser().getEmail();
        this.items = order.getItems().stream()
                .map(i -> new OrderItemDetail(
                        i.getProduct().getId(),
                        i.getProduct().getName(),
                        i.getQuantity()))
                .toList();
    }

    public record OrderItemDetail(Long productId, String productName, int quantity) {}
}
