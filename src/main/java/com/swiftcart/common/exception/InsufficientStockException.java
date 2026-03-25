package com.swiftcart.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when an order cannot be fulfilled because a product
 * has insufficient stock. Distinct from a generic AppException
 * so callers can catch it specifically (e.g. retry logic).
 */
public class InsufficientStockException extends AppException {

    public InsufficientStockException(Long productId, int requested, int available) {
        super(
            String.format(
                "Insufficient stock for product %d: requested %d, available %d",
                productId, requested, available
            ),
            HttpStatus.CONFLICT
        );
    }
}
