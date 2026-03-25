package com.swiftcart.order.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Reacts to OrderCreatedEvent for downstream concerns.
 *
 * In a real system each concern would be its own listener (or its own
 * microservice). They're grouped here for brevity.
 *
 * @Async makes these listeners non-blocking — the order placement
 * response is returned to the client immediately while these run
 * on a background thread. Requires @EnableAsync on the application class.
 */
@Slf4j
@Component
public class OrderEventListener {

    /**
     * Simulate sending an order confirmation email.
     * In production: call an email/SMS service or push to a queue.
     */
    @Async
    @EventListener
    public void onOrderCreated(OrderCreatedEvent event) {
        log.info("[NOTIFICATION] Order confirmation sent to {} for order #{}",
                 event.getUserEmail(), event.getOrderId());

        // Simulate the items being logged for a dispatch system
        event.getItems().forEach(item ->
            log.info("[DISPATCH] Order #{} — {} x{} queued for picking",
                     event.getOrderId(), item.productName(), item.quantity())
        );
    }
}
