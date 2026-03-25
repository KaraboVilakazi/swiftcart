package com.swiftcart.order.repository;

import com.swiftcart.order.domain.Order;
import com.swiftcart.order.domain.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Page<Order> findAllByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<Order> findAllByStatusOrderByCreatedAtDesc(OrderStatus status, Pageable pageable);

    /** Fetch order with items and products in one query. */
    @Query("SELECT o FROM Order o " +
           "LEFT JOIN FETCH o.items i " +
           "LEFT JOIN FETCH i.product " +
           "WHERE o.id = :id AND o.user.id = :userId")
    Optional<Order> findByIdAndUserIdWithItems(@Param("id") Long id, @Param("userId") Long userId);
}
