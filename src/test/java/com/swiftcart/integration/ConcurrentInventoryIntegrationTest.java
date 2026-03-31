package com.swiftcart.integration;

import com.swiftcart.common.exception.AppException;
import com.swiftcart.common.exception.InsufficientStockException;
import com.swiftcart.inventory.domain.Inventory;
import com.swiftcart.inventory.repository.InventoryRepository;
import com.swiftcart.inventory.service.InventoryService;
import com.swiftcart.product.domain.Product;
import com.swiftcart.product.repository.CategoryRepository;
import com.swiftcart.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Concurrency integration test for InventoryService.
 *
 * WHAT THIS TEST PROVES:
 * ─────────────────────
 * The unit tests in InventoryServiceTest verify retry logic in isolation
 * by mocking ObjectOptimisticLockingFailureException. This test goes
 * further — it fires real concurrent threads against a real PostgreSQL
 * database and proves the @Version field prevents overselling end-to-end.
 *
 * SCENARIO:
 *  - 1 product, 5 units of stock
 *  - 10 threads all attempt to deduct 1 unit at the same moment
 *  - Expected: exactly 5 succeed, 5 fail (InsufficientStock or conflict)
 *  - Invariant: final stock is 0, never negative
 *
 * This is the test a real e-commerce system needs. Mocks can't catch
 * a missing @Version annotation or a misconfigured transaction boundary.
 */
@SpringBootTest
@Testcontainers
@DisplayName("Concurrent Inventory — Optimistic Locking Integration Test")
class ConcurrentInventoryIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("swiftcart_concurrent_test")
                    .withUsername("test")
                    .withPassword("test");

    @Autowired InventoryService    inventoryService;
    @Autowired InventoryRepository inventoryRepository;
    @Autowired ProductRepository   productRepository;
    @Autowired CategoryRepository  categoryRepository;

    private static final int INITIAL_STOCK       = 5;
    private static final int CONCURRENT_THREADS  = 10;

    private Long productId;

    @BeforeEach
    void setUp() {
        // Use an existing seeded category — Flyway runs automatically
        var category = categoryRepository.findBySlug("electronics")
                .orElseThrow(() -> new IllegalStateException("Seed data missing: 'electronics' category"));

        // Unique slug per test run so @Column(unique=true) doesn't clash
        Product product = productRepository.save(Product.builder()
                .name("Concurrent Test Item")
                .slug("concurrent-test-" + System.currentTimeMillis())
                .description("Used only by ConcurrentInventoryIntegrationTest")
                .price(new BigDecimal("999.00"))
                .category(category)
                .active(true)
                .build());

        productId = product.getId();

        inventoryRepository.save(Inventory.builder()
                .product(product)
                .quantity(INITIAL_STOCK)
                .build());
    }

    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("10 threads competing for 5 units — exactly 5 succeed, final stock is 0, never negative")
    void concurrentDeductions_neverOversell() throws InterruptedException {
        ExecutorService executor  = Executors.newFixedThreadPool(CONCURRENT_THREADS);
        CountDownLatch  startGate = new CountDownLatch(1);
        CountDownLatch  doneLatch = new CountDownLatch(CONCURRENT_THREADS);

        AtomicInteger succeeded      = new AtomicInteger(0);
        AtomicInteger expectedFailed = new AtomicInteger(0);
        List<Throwable> unexpectedErrors = new CopyOnWriteArrayList<>();

        for (int i = 0; i < CONCURRENT_THREADS; i++) {
            executor.submit(() -> {
                try {
                    startGate.await();   // hold all threads until released together
                    inventoryService.deductStock(productId, 1);
                    succeeded.incrementAndGet();
                } catch (InsufficientStockException e) {
                    // Expected: stock ran out before this thread got a turn
                    expectedFailed.incrementAndGet();
                } catch (AppException e) {
                    // Expected: optimistic lock retries exhausted under high contention
                    expectedFailed.incrementAndGet();
                } catch (Throwable t) {
                    unexpectedErrors.add(t);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startGate.countDown();                        // release all threads at once
        boolean finished = doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finished).as("Test timed out — threads did not complete").isTrue();
        assertThat(unexpectedErrors)
                .as("Unexpected exceptions: " + unexpectedErrors)
                .isEmpty();

        // Core correctness assertions
        assertThat(succeeded.get())
                .as("Exactly INITIAL_STOCK threads should succeed")
                .isEqualTo(INITIAL_STOCK);

        assertThat(expectedFailed.get())
                .as("Remaining threads should fail gracefully")
                .isEqualTo(CONCURRENT_THREADS - INITIAL_STOCK);

        // The strongest assertion — proves no overselling occurred at the DB level
        int finalQuantity = inventoryRepository.findByProductId(productId)
                .map(Inventory::getQuantity)
                .orElseThrow(() -> new AssertionError("Inventory row missing after test"));

        assertThat(finalQuantity)
                .as("Final stock must be exactly 0 — never negative, never skipped")
                .isZero();
    }

    @Test
    @DisplayName("stock rolls back correctly — deduct then restock leaves quantity unchanged")
    void rollbackStock_exactlyRestoresQuantity() {
        int quantityBefore = inventoryRepository.findByProductId(productId)
                .map(Inventory::getQuantity)
                .orElseThrow()
                .getQuantity();

        inventoryService.deductStock(productId, 3);
        inventoryService.rollbackStock(productId, 3);

        int quantityAfter = inventoryRepository.findByProductId(productId)
                .map(Inventory::getQuantity)
                .orElseThrow()
                .getQuantity();

        assertThat(quantityAfter)
                .as("Rollback must exactly restore the quantity deducted")
                .isEqualTo(quantityBefore);
    }
}
