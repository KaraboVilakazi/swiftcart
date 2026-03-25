package com.swiftcart.cart.controller;

import com.swiftcart.cart.dto.CartResponse;
import com.swiftcart.cart.service.CartService;
import com.swiftcart.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping
    public ResponseEntity<ApiResponse<CartResponse>> getCart(
            @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(ApiResponse.ok(cartService.getOrCreateCart(principal.getUsername())));
    }

    /** POST /api/v1/cart/items?productId=1&quantity=2 */
    @PostMapping("/items")
    public ResponseEntity<ApiResponse<CartResponse>> addItem(
            @AuthenticationPrincipal UserDetails principal,
            @RequestParam Long productId,
            @RequestParam(defaultValue = "1") int quantity) {
        return ResponseEntity.ok(ApiResponse.ok(
                cartService.addItem(principal.getUsername(), productId, quantity)));
    }

    /** PUT /api/v1/cart/items/{productId}?quantity=3 */
    @PutMapping("/items/{productId}")
    public ResponseEntity<ApiResponse<CartResponse>> updateItem(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable Long productId,
            @RequestParam int quantity) {
        return ResponseEntity.ok(ApiResponse.ok(
                cartService.updateItem(principal.getUsername(), productId, quantity)));
    }

    /** DELETE /api/v1/cart/items/{productId} */
    @DeleteMapping("/items/{productId}")
    public ResponseEntity<ApiResponse<CartResponse>> removeItem(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable Long productId) {
        return ResponseEntity.ok(ApiResponse.ok(
                cartService.removeItem(principal.getUsername(), productId)));
    }
}
