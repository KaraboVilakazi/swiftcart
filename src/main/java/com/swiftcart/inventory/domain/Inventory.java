package com.swiftcart.inventory.domain;

import com.swiftcart.product.domain.Product;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Inventory entity — tracks stock for a single product.
 *
 * CONCURRENCY DESIGN:
 * ------------------
 * The {@code version} field is annotated with {@code @Version}, which
 * enables JPA Optimistic Locking.
 *
 * How it works:
 *   1. Thread A reads inventory row: quantity=10, version=5
 *   2. Thread B reads the same row:  quantity=10, version=5
 *   3. Thread A deducts 3 units, issues:
 *        UPDATE inventory SET quantity=7, version=6 WHERE id=X AND version=5
 *      → succeeds
 *   4. Thread B also tries to deduct 2 units:
 *        UPDATE inventory SET quantity=8, version=6 WHERE id=X AND version=5
 *      → 0 rows affected → JPA throws OptimisticLockException
 *   5. InventoryService catches the exception and retries (up to 3 times).
 *
 * Why optimistic over pessimistic?
 *   - No DB-level row lock is held between read and write, so throughput
 *     is much higher under moderate contention.
 *   - Pessimistic locking (SELECT FOR UPDATE) would be preferable only
 *     when contention is very high and retries become expensive.
 *
 * CHECK constraint (quantity >= 0) in the DB provides a final safety net
 * in case a bug bypasses the application-level check.
 */
@Entity
@Table(name = "inventory")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false, unique = true)
    private Product product;

    @Column(nullable = false)
    private int quantity;

    /**
     * Optimistic lock version — managed by JPA.
     * Must NOT be set manually; only JPA increments it.
     */
    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }

    // ------------------------------------------------------------------ //
    // Domain behaviour — stock operations are methods on the entity so
    // the rule "quantity must not go below zero" is enforced in one place.
    // ------------------------------------------------------------------ //

    /**
     * Deduct stock. Throws IllegalStateException if there is insufficient
     * quantity — the service layer converts this to an AppException.
     */
    public void deduct(int amount) {
        if (amount <= 0) throw new IllegalArgumentException("Deduct amount must be positive");
        if (this.quantity < amount) {
            throw new IllegalStateException(
                String.format("Insufficient stock: requested %d, available %d", amount, this.quantity)
            );
        }
        this.quantity -= amount;
    }

    public void restock(int amount) {
        if (amount <= 0) throw new IllegalArgumentException("Restock amount must be positive");
        this.quantity += amount;
    }
}
