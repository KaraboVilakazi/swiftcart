package com.swiftcart.product.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record CreateProductRequest(

    @NotBlank(message = "Product name is required")
    String name,

    @NotBlank(message = "Slug is required")
    String slug,

    String description,

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.01", message = "Price must be greater than zero")
    @Digits(integer = 10, fraction = 2, message = "Price format invalid")
    BigDecimal price,

    Long categoryId,

    @NotNull(message = "Initial stock quantity is required")
    @Min(value = 0, message = "Stock quantity cannot be negative")
    Integer initialStock
) {}
