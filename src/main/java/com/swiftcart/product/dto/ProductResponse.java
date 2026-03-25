package com.swiftcart.product.dto;

import com.swiftcart.product.domain.Product;

import java.math.BigDecimal;
import java.time.Instant;

public record ProductResponse(
    Long       id,
    String     name,
    String     slug,
    String     description,
    BigDecimal price,
    Long       categoryId,
    String     categoryName,
    boolean    active,
    Instant    createdAt
) {
    public static ProductResponse from(Product p) {
        return new ProductResponse(
            p.getId(),
            p.getName(),
            p.getSlug(),
            p.getDescription(),
            p.getPrice(),
            p.getCategory() != null ? p.getCategory().getId()   : null,
            p.getCategory() != null ? p.getCategory().getName() : null,
            p.isActive(),
            p.getCreatedAt()
        );
    }
}
