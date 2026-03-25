package com.swiftcart.order.service;

import com.swiftcart.cart.domain.Cart;
import com.swiftcart.cart.domain.CartItem;
import com.swiftcart.cart.repository.CartRepository;
import com.swiftcart.cart.service.CartService;
import com.swiftcart.common.exception.AppException;
import com.swiftcart.common.exception.InsufficientStockException;
import com.swiftcart.inventory.service.InventoryService;
import com.swiftcart.order.domain.Order;
import com.swiftcart.order.domain.OrderItem;
import com.swiftcart.order.domain.OrderStatus;
import com.swiftcart.order.dto.OrderResponse;
import com.swiftcart.order.dto.PlaceOrderRequest;
import com.swiftcart.order.event.OrderCreatedEvent;
import com.swiftcart.order.repository.OrderRepository;
import com.swiftcart.user.domain.User;
import com.swiftcart.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * OrderService — the most critical service in the system.
 *
 * ORDER PLACEMENT FLOW:
 * ─────────────────────
 *  1. Validate cart is not empty
 *  2. Deduct inventory for each line item (with optimistic lock retry)
 *     — if any item fails, rollback all previously deducted items
 *  3. Persist the Order entity
 *  4. Clear the customer's cart
 *  5. Publish OrderCreatedEvent (async notification, dispatch queue)
 *
 * FAILURE HANDLING:
 * ─────────────────
 *  • InsufficientStockException on any line item → rollback deducted
 *    items, return 409 Conflict to client.
 *  • If order persistence fails → @Transactional rolls back the DB
 *    write; inventory was already deducted outside this transaction,
 *    so rollbackStock() is called explicitly in the catch block.
 *  • Cart clear failure is non-fatal — the order is already placed.
 *
 * WHY INVENTORY DEDUCTION HAPPENS BEFORE order.save()?
 * ─────────────────────────────────────────────────────
 *  Deducting stock first (and rolling back if any item fails) prevents
 *  overselling. If we saved the order first and then deduction failed,
 *  we'd have a CONFIRMED order with no reserved stock.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository        orderRepository;
    private final CartRepository         cartRepository;
    private final UserRepository         userRepository;
    private final InventoryService       inventoryService;
    private final CartService            cartService;
    private final ApplicationEventPublisher eventPublisher;

    // ------------------------------------------------------------------ //
    // Place order
    // ------------------------------------------------------------------ //

    @Transactional
    public OrderResponse placeOrder(String email, PlaceOrderRequest request) {
        User user = findUser(email);
        Cart cart = cartRepository.findByUserIdWithItems(user.getId())
                .orElseThrow(() -> AppException.badRequest("Cart is empty"));

        if (cart.getItems().isEmpty()) {
            throw AppException.badRequest("Cannot place an order with an empty cart");
        }

        // Track which items we've successfully deducted so we can roll back on failure
        List<CartItem> deducted = new ArrayList<>();

        try {
            // --- Step 1: Deduct inventory for every line item ---
            for (CartItem cartItem : cart.getItems()) {
                inventoryService.deductStock(
                        cartItem.getProduct().getId(),
                        cartItem.getQuantity()
                );
                deducted.add(cartItem);
            }

            // --- Step 2: Build and persist the order ---
            Order order = buildOrder(user, cart, request.shippingAddress());
            order = orderRepository.save(order);

            log.info("Order placed: id={} user={} total={}", order.getId(), email, order.getTotalAmount());

            // --- Step 3: Clear cart ---
            cartService.clearCart(user.getId());

            // --- Step 4: Publish domain event (async) ---
            eventPublisher.publishEvent(new OrderCreatedEvent(this, order));

            return OrderResponse.from(order);

        } catch (InsufficientStockException ex) {
            // Roll back any items we already deducted before hitting the short stock
            rollbackDeducted(deducted);
            throw ex;   // re-throw: GlobalExceptionHandler maps to 409

        } catch (Exception ex) {
            // Unexpected failure — roll back all deductions
            rollbackDeducted(deducted);
            log.error("Order placement failed for user={}: {}", email, ex.getMessage(), ex);
            throw AppException.unprocessable("Order could not be completed. Please try again.");
        }
    }

    // ------------------------------------------------------------------ //
    // Query
    // ------------------------------------------------------------------ //

    @Transactional(readOnly = true)
    public Page<OrderResponse> getMyOrders(String email, Pageable pageable) {
        User user = findUser(email);
        return orderRepository.findAllByUserIdOrderByCreatedAtDesc(user.getId(), pageable)
                .map(OrderResponse::from);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderDetail(String email, Long orderId) {
        User user = findUser(email);
        return orderRepository.findByIdAndUserIdWithItems(orderId, user.getId())
                .map(OrderResponse::from)
                .orElseThrow(() -> AppException.notFound("Order not found: " + orderId));
    }

    // ------------------------------------------------------------------ //
    // Internal helpers
    // ------------------------------------------------------------------ //

    private Order buildOrder(User user, Cart cart, String shippingAddress) {
        Order order = Order.builder()
                .user(user)
                .status(OrderStatus.CONFIRMED)
                .totalAmount(cart.getTotal())
                .shippingAddress(shippingAddress)
                .build();

        List<OrderItem> orderItems = cart.getItems().stream()
                .map(ci -> OrderItem.builder()
                        .order(order)
                        .product(ci.getProduct())
                        .quantity(ci.getQuantity())
                        .unitPrice(ci.getUnitPrice())  // locked price
                        .build())
                .toList();

        order.getItems().addAll(orderItems);
        return order;
    }

    private void rollbackDeducted(List<CartItem> deducted) {
        for (CartItem item : deducted) {
            try {
                inventoryService.rollbackStock(item.getProduct().getId(), item.getQuantity());
            } catch (Exception rollbackEx) {
                // Log but don't suppress — a stock discrepancy alert should be
                // raised here in production (e.g. PagerDuty, Sentry).
                log.error("CRITICAL: Failed to rollback stock for product {}: {}",
                          item.getProduct().getId(), rollbackEx.getMessage());
            }
        }
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> AppException.notFound("User not found"));
    }
}
