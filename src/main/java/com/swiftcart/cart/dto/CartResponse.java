package com.swiftcart.cart.dto;

import com.swiftcart.cart.domain.Cart;
import com.swiftcart.cart.domain.CartItem;

import java.math.BigDecimal;
import java.util.List;

public record CartResponse(
    Long            cartId,
    List<CartItemResponse> items,
    BigDecimal      total,
    int             itemCount
) {
    public record CartItemResponse(
        Long       itemId,
        Long       productId,
        String     productName,
        int        quantity,
        BigDecimal unitPrice,
        BigDecimal subtotal
    ) {
        static CartItemResponse from(CartItem item) {
            return new CartItemResponse(
                item.getId(),
                item.getProduct().getId(),
                item.getProduct().getName(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getSubtotal()
            );
        }
    }

    public static CartResponse from(Cart cart) {
        List<CartItemResponse> items = cart.getItems().stream()
                .map(CartItemResponse::from)
                .toList();

        return new CartResponse(
            cart.getId(),
            items,
            cart.getTotal(),
            items.size()
        );
    }
}
