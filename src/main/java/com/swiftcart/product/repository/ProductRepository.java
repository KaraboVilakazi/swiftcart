package com.swiftcart.product.repository;

import com.swiftcart.product.domain.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Page<Product> findAllByActiveTrue(Pageable pageable);

    Page<Product> findAllByCategoryIdAndActiveTrue(Long categoryId, Pageable pageable);

    Optional<Product> findBySlugAndActiveTrue(String slug);

    boolean existsBySlug(String slug);

    /** Fetch product with its category in one query to avoid N+1. */
    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.category WHERE p.id = :id AND p.active = true")
    Optional<Product> findActiveByIdWithCategory(@Param("id") Long id);

    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.category " +
           "WHERE LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%')) AND p.active = true")
    Page<Product> searchByName(@Param("query") String query, Pageable pageable);
}
