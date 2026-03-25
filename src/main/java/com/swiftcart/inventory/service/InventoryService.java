package com.swiftcart.inventory.service;

import com.swiftcart.common.exception.AppException;
import com.swiftcart.common.exception.InsufficientStockException;
import com.swiftcart.inventory.domain.Inventory;
import com.swiftcart.inventory.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * InventoryService — deducts and restocks product inventory.
 *
 * Retry pattern for optimistic locking:
 * ------------------------------------
 * When two threads concurrently try to deduct the same product's stock,
 * the second writer will hit an ObjectOptimisticLockingFailureException.
 * Rather than surfacing this to the user, we retry the operation up to
 * {@code maxRetryAttempts} times with a short sleep between attempts.
 *
 * This gives a consistent user experience while maintaining correctness:
 * no transaction holds a lock between retries, so other operations are
 * not blocked.
 *
 * If all retries are exhausted it likely means extreme contention on a
 * single product, at which point we surface a 409 Conflict to the caller.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;

    @Value("${app.inventory.max-retry-attempts:3}")
    private int maxRetryAttempts;

    @Value("${app.inventory.retry-delay-ms:100}")
    private long retryDelayMs;

    // ------------------------------------------------------------------ //
    // Stock query
    // ------------------------------------------------------------------ //

    @Transactional(readOnly = true)
    public Map<String, Object> getStock(Long productId) {
        Inventory inv = findOrThrow(productId);
        return Map.of(
            "productId", productId,
            "quantity",  inv.getQuantity(),
            "version",   inv.getVersion()
        );
    }

    // ------------------------------------------------------------------ //
    // Deduction (called during order placement)
    // ------------------------------------------------------------------ //

    /**
     * Deduct {@code quantity} units from product stock with optimistic locking.
     * Retries up to {@code maxRetryAttempts} times on version conflicts.
     */
    public void deductStock(Long productId, int quantity) {
        int attempt = 0;
        while (true) {
            try {
                attemptDeduction(productId, quantity);
                return;   // success
            } catch (ObjectOptimisticLockingFailureException ex) {
                attempt++;
                log.warn("Optimistic lock conflict for product {} — attempt {}/{}",
                         productId, attempt, maxRetryAttempts);
                if (attempt >= maxRetryAttempts) {
                    throw AppException.conflict(
                        "Could not reserve stock for product " + productId +
                        " after " + maxRetryAttempts + " attempts. Please try again."
                    );
                }
                sleep(retryDelayMs);
            }
        }
    }

    @Transactional
    protected void attemptDeduction(Long productId, int quantity) {
        Inventory inv = findOrThrow(productId);
        int available = inv.getQuantity();

        if (available < quantity) {
            throw new InsufficientStockException(productId, quantity, available);
        }

        inv.deduct(quantity);
        inventoryRepository.save(inv);
        log.debug("Stock deducted: product={} qty={} remaining={}", productId, quantity, inv.getQuantity());
    }

    // ------------------------------------------------------------------ //
    // Restock (admin operation or after order cancellation)
    // ------------------------------------------------------------------ //

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void restock(Long productId, int quantity) {
        Inventory inv = findOrThrow(productId);
        inv.restock(quantity);
        inventoryRepository.save(inv);
        log.info("Stock restocked: product={} qty={} newTotal={}", productId, quantity, inv.getQuantity());
    }

    /**
     * Rollback stock after a failed order (e.g. payment failure).
     * This is always safe to call — it simply adds units back.
     */
    @Transactional
    public void rollbackStock(Long productId, int quantity) {
        Inventory inv = findOrThrow(productId);
        inv.restock(quantity);
        inventoryRepository.save(inv);
        log.info("Stock rolled back: product={} qty={}", productId, quantity);
    }

    // ------------------------------------------------------------------ //
    // Internal helpers
    // ------------------------------------------------------------------ //

    private Inventory findOrThrow(Long productId) {
        return inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> AppException.notFound("Inventory not found for product: " + productId));
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
