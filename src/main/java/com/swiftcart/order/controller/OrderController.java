package com.swiftcart.order.controller;

import com.swiftcart.common.response.ApiResponse;
import com.swiftcart.order.dto.OrderResponse;
import com.swiftcart.order.dto.PlaceOrderRequest;
import com.swiftcart.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /** POST /api/v1/orders — place order from current cart */
    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> placeOrder(
            @AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody PlaceOrderRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.created(orderService.placeOrder(principal.getUsername(), request)));
    }

    /** GET /api/v1/orders */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<OrderResponse>>> getMyOrders(
            @AuthenticationPrincipal UserDetails principal,
            @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(
                orderService.getMyOrders(principal.getUsername(), pageable)));
    }

    /** GET /api/v1/orders/{id} */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrderDetail(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(
                orderService.getOrderDetail(principal.getUsername(), id)));
    }
}
