package com.swiftcart.order;

import com.swiftcart.cart.domain.Cart;
import com.swiftcart.cart.domain.CartItem;
import com.swiftcart.cart.repository.CartRepository;
import com.swiftcart.cart.service.CartService;
import com.swiftcart.common.exception.AppException;
import com.swiftcart.common.exception.InsufficientStockException;
import com.swiftcart.inventory.service.InventoryService;
import com.swiftcart.order.domain.Order;
import com.swiftcart.order.domain.OrderStatus;
import com.swiftcart.order.dto.OrderResponse;
import com.swiftcart.order.dto.PlaceOrderRequest;
import com.swiftcart.order.event.OrderCreatedEvent;
import com.swiftcart.order.repository.OrderRepository;
import com.swiftcart.order.service.OrderService;
import com.swiftcart.product.domain.Category;
import com.swiftcart.product.domain.Product;
import com.swiftcart.user.domain.Role;
import com.swiftcart.user.domain.User;
import com.swiftcart.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for OrderService.
 *
 * All dependencies are mocked — this tests pure business logic:
 *   - Successful order placement
 *   - Stock rollback when one item has insufficient stock
 *   - Stock rollback on unexpected failures
 *   - Empty cart rejection
 *   - OrderCreatedEvent is published on success
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService")
class OrderServiceTest {

    @Mock OrderRepository        orderRepository;
    @Mock CartRepository         cartRepository;
    @Mock UserRepository         userRepository;
    @Mock InventoryService       inventoryService;
    @Mock CartService            cartService;
    @Mock ApplicationEventPublisher eventPublisher;

    @InjectMocks
    OrderService orderService;

    private User        testUser;
    private Product     productA;
    private Product     productB;
    private Cart        cart;
    private CartItem    itemA;
    private CartItem    itemB;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .email("karabo@swiftcart.co.za")
                .firstName("Karabo")
                .lastName("V")
                .role(Role.CUSTOMER)
                .passwordHash("hashed")
                .build();

        Category electronics = Category.builder()
                .id(1L).name("Electronics").slug("electronics").build();

        productA = Product.builder()
                .id(1L).name("Samsung Galaxy S24").slug("samsung-galaxy-s24")
                .price(new BigDecimal("18999.00")).category(electronics).active(true).build();

        productB = Product.builder()
                .id(2L).name("Dell XPS 15").slug("dell-xps-15")
                .price(new BigDecimal("28999.00")).category(electronics).active(true).build();

        cart  = Cart.builder().id(1L).user(testUser).build();
        itemA = CartItem.builder().id(1L).cart(cart).product(productA)
                        .quantity(2).unitPrice(new BigDecimal("18999.00")).build();
        itemB = CartItem.builder().id(2L).cart(cart).product(productB)
                        .quantity(1).unitPrice(new BigDecimal("28999.00")).build();
        cart.getItems().addAll(List.of(itemA, itemB));
    }

    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("placeOrder()")
    class PlaceOrder {

        @Test
        @DisplayName("successfully places order, deducts stock, clears cart, publishes event")
        void placeOrder_success() {
            when(userRepository.findByEmail(testUser.getEmail())).thenReturn(Optional.of(testUser));
            when(cartRepository.findByUserIdWithItems(testUser.getId())).thenReturn(Optional.of(cart));
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
                Order o = inv.getArgument(0);
                // Simulate DB-assigned ID
                return Order.builder()
                        .id(99L).user(o.getUser()).status(OrderStatus.CONFIRMED)
                        .totalAmount(o.getTotalAmount()).shippingAddress(o.getShippingAddress())
                        .build();
            });

            PlaceOrderRequest request = new PlaceOrderRequest("123 Sandton Drive, Johannesburg");
            OrderResponse response = orderService.placeOrder(testUser.getEmail(), request);

            // Stock deducted for both items
            verify(inventoryService).deductStock(productA.getId(), 2);
            verify(inventoryService).deductStock(productB.getId(), 1);

            // Cart cleared
            verify(cartService).clearCart(testUser.getId());

            // Event published
            verify(eventPublisher).publishEvent(any(OrderCreatedEvent.class));

            // Response is correct
            assertThat(response.status()).isEqualTo("CONFIRMED");
            assertThat(response.shippingAddress()).isEqualTo("123 Sandton Drive, Johannesburg");
        }

        @Test
        @DisplayName("rolls back already-deducted stock when second item has insufficient stock")
        void placeOrder_rollsBackStock_onInsufficientStock() {
            when(userRepository.findByEmail(testUser.getEmail())).thenReturn(Optional.of(testUser));
            when(cartRepository.findByUserIdWithItems(testUser.getId())).thenReturn(Optional.of(cart));

            // First item deducts fine, second throws InsufficientStockException
            doNothing().when(inventoryService).deductStock(productA.getId(), 2);
            doThrow(new InsufficientStockException(productB.getId(), 1, 0))
                    .when(inventoryService).deductStock(productB.getId(), 1);

            PlaceOrderRequest request = new PlaceOrderRequest("123 Sandton Drive, Johannesburg");

            assertThatThrownBy(() -> orderService.placeOrder(testUser.getEmail(), request))
                    .isInstanceOf(InsufficientStockException.class);

            // Product A stock must be rolled back
            verify(inventoryService).rollbackStock(productA.getId(), 2);

            // Order must NOT be saved
            verify(orderRepository, never()).save(any());

            // No event published
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("rolls back all stock on unexpected exception during order save")
        void placeOrder_rollsBackAllStock_onUnexpectedException() {
            when(userRepository.findByEmail(testUser.getEmail())).thenReturn(Optional.of(testUser));
            when(cartRepository.findByUserIdWithItems(testUser.getId())).thenReturn(Optional.of(cart));
            when(orderRepository.save(any())).thenThrow(new RuntimeException("DB connection lost"));

            PlaceOrderRequest request = new PlaceOrderRequest("123 Sandton Drive, Johannesburg");

            assertThatThrownBy(() -> orderService.placeOrder(testUser.getEmail(), request))
                    .isInstanceOf(AppException.class)
                    .hasMessageContaining("could not be completed");

            // Both items must be rolled back
            verify(inventoryService).rollbackStock(productA.getId(), 2);
            verify(inventoryService).rollbackStock(productB.getId(), 1);
        }

        @Test
        @DisplayName("rejects order when cart is empty")
        void placeOrder_rejects_emptyCart() {
            Cart emptyCart = Cart.builder().id(2L).user(testUser).build();

            when(userRepository.findByEmail(testUser.getEmail())).thenReturn(Optional.of(testUser));
            when(cartRepository.findByUserIdWithItems(testUser.getId())).thenReturn(Optional.of(emptyCart));

            PlaceOrderRequest request = new PlaceOrderRequest("123 Sandton Drive");

            assertThatThrownBy(() -> orderService.placeOrder(testUser.getEmail(), request))
                    .isInstanceOf(AppException.class)
                    .hasMessageContaining("empty");

            verify(inventoryService, never()).deductStock(any(), anyInt());
        }

        @Test
        @DisplayName("rejects order when cart does not exist")
        void placeOrder_rejects_missingCart() {
            when(userRepository.findByEmail(testUser.getEmail())).thenReturn(Optional.of(testUser));
            when(cartRepository.findByUserIdWithItems(testUser.getId())).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    orderService.placeOrder(testUser.getEmail(), new PlaceOrderRequest("addr")))
                    .isInstanceOf(AppException.class);

            verify(inventoryService, never()).deductStock(any(), anyInt());
        }

        @Test
        @DisplayName("publishes OrderCreatedEvent with correct order details")
        void placeOrder_publishesEvent_withCorrectDetails() {
            when(userRepository.findByEmail(testUser.getEmail())).thenReturn(Optional.of(testUser));
            when(cartRepository.findByUserIdWithItems(testUser.getId())).thenReturn(Optional.of(cart));
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

            orderService.placeOrder(testUser.getEmail(), new PlaceOrderRequest("123 Main St"));

            ArgumentCaptor<OrderCreatedEvent> eventCaptor = ArgumentCaptor.forClass(OrderCreatedEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());

            OrderCreatedEvent event = eventCaptor.getValue();
            assertThat(event.getUserEmail()).isEqualTo(testUser.getEmail());
            assertThat(event.getItems()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("getOrderDetail()")
    class GetOrderDetail {

        @Test
        @DisplayName("throws not found when order belongs to different user")
        void getOrderDetail_notFound_wrongUser() {
            when(userRepository.findByEmail(testUser.getEmail())).thenReturn(Optional.of(testUser));
            when(orderRepository.findByIdAndUserIdWithItems(999L, testUser.getId()))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.getOrderDetail(testUser.getEmail(), 999L))
                    .isInstanceOf(AppException.class);
        }
    }
}
