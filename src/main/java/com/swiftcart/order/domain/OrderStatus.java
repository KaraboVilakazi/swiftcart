package com.swiftcart.order.domain;

/**
 * Order lifecycle states.
 *
 * PENDING → CONFIRMED → SHIPPED → DELIVERED
 *         ↘ CANCELLED (from PENDING or CONFIRMED)
 *         ↘ PAYMENT_FAILED (from PENDING)
 */
public enum OrderStatus {
    PENDING,
    CONFIRMED,
    PAYMENT_FAILED,
    SHIPPED,
    DELIVERED,
    CANCELLED
}
