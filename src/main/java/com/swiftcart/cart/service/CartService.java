package com.swiftcart.cart.service;

import com.swiftcart.cart.domain.Cart;
import com.swiftcart.cart.domain.CartItem;
import com.swiftcart.cart.dto.CartResponse;
import com.swiftcart.cart.repository.CartRepository;
import com.swiftcart.common.exception.AppException;
import com.swiftcart.product.domain.Product;
import com.swiftcart.product.repository.ProductRepository;
import com.swiftcart.user.domain.User;
import com.swiftcart.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRepository    cartRepository;
    private final ProductRepository productRepository;
    private final UserRepository    userRepository;

    // ------------------------------------------------------------------ //
    // Get cart (or create one if first visit)
    // ------------------------------------------------------------------ //

    @Transactional
    public CartResponse getOrCreateCart(String email) {
        User user = findUser(email);
        Cart cart = cartRepository.findByUserIdWithItems(user.getId())
                .orElseGet(() -> createCart(user));
        return CartResponse.from(cart);
    }

    // ------------------------------------------------------------------ //
    // Add item
    // ------------------------------------------------------------------ //

    @Transactional
    public CartResponse addItem(String email, Long productId, int quantity) {
        if (quantity <= 0) throw AppException.badRequest("Quantity must be positive");

        User    user    = findUser(email);
        Product product = findActiveProduct(productId);
        Cart    cart    = cartRepository.findByUserIdWithItems(user.getId())
                                        .orElseGet(() -> createCart(user));

        // If product already in cart, increment quantity
        cart.getItems().stream()
                .filter(i -> i.getProduct().getId().equals(productId))
                .findFirst()
                .ifPresentOrElse(
                    existing -> existing.setQuantity(existing.getQuantity() + quantity),
                    () -> cart.getItems().add(
                        CartItem.builder()
                            .cart(cart)
                            .product(product)
                            .quantity(quantity)
                            .unitPrice(product.getPrice())  // snapshot current price
                            .build()
                    )
                );

        cartRepository.save(cart);
        log.debug("Item added to cart: user={} product={} qty={}", email, productId, quantity);
        return CartResponse.from(cart);
    }

    // ------------------------------------------------------------------ //
    // Update quantity
    // ------------------------------------------------------------------ //

    @Transactional
    public CartResponse updateItem(String email, Long productId, int quantity) {
        if (quantity < 0) throw AppException.badRequest("Quantity cannot be negative");

        User user = findUser(email);
        Cart cart = cartRepository.findByUserIdWithItems(user.getId())
                .orElseThrow(() -> AppException.notFound("Cart not found"));

        if (quantity == 0) {
            // Remove item entirely
            cart.getItems().removeIf(i -> i.getProduct().getId().equals(productId));
        } else {
            cart.getItems().stream()
                    .filter(i -> i.getProduct().getId().equals(productId))
                    .findFirst()
                    .orElseThrow(() -> AppException.notFound("Product not in cart"))
                    .setQuantity(quantity);
        }

        cartRepository.save(cart);
        return CartResponse.from(cart);
    }

    // ------------------------------------------------------------------ //
    // Remove item
    // ------------------------------------------------------------------ //

    @Transactional
    public CartResponse removeItem(String email, Long productId) {
        return updateItem(email, productId, 0);
    }

    // ------------------------------------------------------------------ //
    // Clear cart (called after successful order)
    // ------------------------------------------------------------------ //

    @Transactional
    public void clearCart(Long userId) {
        cartRepository.findByUserIdWithItems(userId).ifPresent(cart -> {
            cart.clear();
            cartRepository.save(cart);
        });
    }

    // ------------------------------------------------------------------ //
    // Internal helpers
    // ------------------------------------------------------------------ //

    private Cart createCart(User user) {
        Cart cart = Cart.builder().user(user).build();
        return cartRepository.save(cart);
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> AppException.notFound("User not found: " + email));
    }

    private Product findActiveProduct(Long productId) {
        return productRepository.findActiveByIdWithCategory(productId)
                .orElseThrow(() -> AppException.notFound("Product not found or inactive: " + productId));
    }
}
