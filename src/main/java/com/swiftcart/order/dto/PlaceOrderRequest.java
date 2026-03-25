package com.swiftcart.order.dto;

import jakarta.validation.constraints.NotBlank;

public record PlaceOrderRequest(

    @NotBlank(message = "Shipping address is required")
    String shippingAddress
) {}
