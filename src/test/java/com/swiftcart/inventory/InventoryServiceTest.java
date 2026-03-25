package com.swiftcart.inventory;

import com.swiftcart.common.exception.AppException;
import com.swiftcart.common.exception.InsufficientStockException;
import com.swiftcart.inventory.domain.Inventory;
import com.swiftcart.inventory.repository.InventoryRepository;
import com.swiftcart.inventory.service.InventoryService;
import com.swiftcart.product.domain.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for InventoryService.
 *
 * Key scenarios tested:
 *  - Successful deduction
 *  - InsufficientStockException when quantity unavailable
 *  - Optimistic lock retry — succeeds on second attempt
 *  - Optimistic lock exhausted — throws conflict after max retries
 *  - Restock increases quantity correctly
 *  - Rollback calls restock
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryService")
class InventoryServiceTest {

    @Mock InventoryRepository inventoryRepository;

    @InjectMocks
    InventoryService inventoryService;

    private Product   product;
    private Inventory inventory;

    @BeforeEach
    void setUp() {
        product = Product.builder()
                .id(1L).name("Samsung Galaxy S24").slug("samsung-galaxy-s24")
                .price(new BigDecimal("18999.00")).active(true).build();

        inventory = Inventory.builder()
                .id(1L).product(product).quantity(10).build();

        // Inject retry config so tests don't sleep for real
        ReflectionTestUtils.setField(inventoryService, "maxRetryAttempts", 3);
        ReflectionTestUtils.setField(inventoryService, "retryDelayMs", 0L);
    }

    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("deductStock()")
    class DeductStock {

        @Test
        @DisplayName("successfully deducts stock when quantity is available")
        void deductStock_success() {
            when(inventoryRepository.findByProductId(1L)).thenReturn(Optional.of(inventory));
            when(inventoryRepository.save(any())).thenReturn(inventory);

            inventoryService.deductStock(1L, 3);

            assertThat(inventory.getQuantity()).isEqualTo(7);
            verify(inventoryRepository).save(inventory);
        }

        @Test
        @DisplayName("throws InsufficientStockException when stock is too low")
        void deductStock_insufficient() {
            when(inventoryRepository.findByProductId(1L)).thenReturn(Optional.of(inventory));

            assertThatThrownBy(() -> inventoryService.deductStock(1L, 15))
                    .isInstanceOf(InsufficientStockException.class)
                    .hasMessageContaining("requested 15")
                    .hasMessageContaining("available 10");
        }

        @Test
        @DisplayName("retries once on optimistic lock conflict then succeeds")
        void deductStock_retries_onOptimisticLock() {
            // First call: lock conflict. Second call: succeeds.
            when(inventoryRepository.findByProductId(1L))
                    .thenReturn(Optional.of(inventory))  // retry 1
                    .thenReturn(Optional.of(inventory)); // retry 2

            when(inventoryRepository.save(any()))
                    .thenThrow(new ObjectOptimisticLockingFailureException(Inventory.class, 1L))
                    .thenReturn(inventory);

            // Should not throw — retried successfully
            assertThatNoException().isThrownBy(() -> inventoryService.deductStock(1L, 2));

            verify(inventoryRepository, times(2)).save(any());
        }

        @Test
        @DisplayName("throws conflict after exhausting all retry attempts")
        void deductStock_exhaustsRetries_throwsConflict() {
            when(inventoryRepository.findByProductId(1L)).thenReturn(Optional.of(inventory));
            when(inventoryRepository.save(any()))
                    .thenThrow(new ObjectOptimisticLockingFailureException(Inventory.class, 1L));

            assertThatThrownBy(() -> inventoryService.deductStock(1L, 2))
                    .isInstanceOf(AppException.class)
                    .hasMessageContaining("3 attempts");

            // Should have tried exactly maxRetryAttempts times
            verify(inventoryRepository, times(3)).save(any());
        }

        @Test
        @DisplayName("throws not found when product has no inventory record")
        void deductStock_notFound() {
            when(inventoryRepository.findByProductId(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> inventoryService.deductStock(99L, 1))
                    .isInstanceOf(AppException.class)
                    .hasMessageContaining("not found");
        }
    }

    @Nested
    @DisplayName("restock()")
    class Restock {

        @Test
        @DisplayName("increases stock quantity correctly")
        void restock_success() {
            when(inventoryRepository.findByProductId(1L)).thenReturn(Optional.of(inventory));
            when(inventoryRepository.save(any())).thenReturn(inventory);

            inventoryService.restock(1L, 5);

            assertThat(inventory.getQuantity()).isEqualTo(15);
        }
    }

    @Nested
    @DisplayName("rollbackStock()")
    class RollbackStock {

        @Test
        @DisplayName("adds quantity back to inventory")
        void rollbackStock_addsQuantityBack() {
            inventory = Inventory.builder().id(1L).product(product).quantity(5).build();
            when(inventoryRepository.findByProductId(1L)).thenReturn(Optional.of(inventory));
            when(inventoryRepository.save(any())).thenReturn(inventory);

            inventoryService.rollbackStock(1L, 3);

            assertThat(inventory.getQuantity()).isEqualTo(8);
        }
    }
}
