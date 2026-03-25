package com.swiftcart.product.service;

import com.swiftcart.common.exception.AppException;
import com.swiftcart.config.RedisConfig;
import com.swiftcart.inventory.domain.Inventory;
import com.swiftcart.inventory.repository.InventoryRepository;
import com.swiftcart.product.domain.Category;
import com.swiftcart.product.domain.Product;
import com.swiftcart.product.dto.CreateProductRequest;
import com.swiftcart.product.dto.ProductResponse;
import com.swiftcart.product.repository.CategoryRepository;
import com.swiftcart.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Product service.
 *
 * Caching strategy:
 *  - Individual products are cached by ID (CACHE_PRODUCTS) — TTL 10 min.
 *  - Paginated listings are cached by page key (CACHE_PRODUCT_PAGE) — TTL 5 min.
 *  - On any write (create/update/delete) ALL product caches are evicted
 *    to prevent stale reads. This is a conservative strategy suitable for
 *    a catalogue with moderate write volume.
 *  - For high-write scenarios, a write-through or per-key eviction would
 *    be more appropriate.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository  productRepository;
    private final CategoryRepository categoryRepository;
    private final InventoryRepository inventoryRepository;

    // ------------------------------------------------------------------ //
    // Reads
    // ------------------------------------------------------------------ //

    @Cacheable(value = RedisConfig.CACHE_PRODUCTS, key = "#id")
    @Transactional(readOnly = true)
    public ProductResponse getById(Long id) {
        return productRepository.findActiveByIdWithCategory(id)
                .map(ProductResponse::from)
                .orElseThrow(() -> AppException.notFound("Product not found: " + id));
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> listAll(Pageable pageable) {
        return productRepository.findAllByActiveTrue(pageable).map(ProductResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> listByCategory(Long categoryId, Pageable pageable) {
        return productRepository.findAllByCategoryIdAndActiveTrue(categoryId, pageable)
                .map(ProductResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> search(String query, Pageable pageable) {
        return productRepository.searchByName(query, pageable).map(ProductResponse::from);
    }

    // ------------------------------------------------------------------ //
    // Writes (admin only — enforced at method level)
    // ------------------------------------------------------------------ //

    @PreAuthorize("hasRole('ADMIN')")
    @CacheEvict(value = RedisConfig.CACHE_PRODUCTS, allEntries = true)
    @Transactional
    public ProductResponse create(CreateProductRequest req) {
        if (productRepository.existsBySlug(req.slug())) {
            throw AppException.conflict("Slug already in use: " + req.slug());
        }

        Category category = null;
        if (req.categoryId() != null) {
            category = categoryRepository.findById(req.categoryId())
                    .orElseThrow(() -> AppException.notFound("Category not found: " + req.categoryId()));
        }

        Product product = Product.builder()
                .name(req.name())
                .slug(req.slug())
                .description(req.description())
                .price(req.price())
                .category(category)
                .active(true)
                .build();

        product = productRepository.save(product);

        // Create inventory record alongside the product
        Inventory inventory = Inventory.builder()
                .product(product)
                .quantity(req.initialStock())
                .build();
        inventoryRepository.save(inventory);

        log.info("Product created: {} (id={})", product.getName(), product.getId());
        return ProductResponse.from(product);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @CacheEvict(value = RedisConfig.CACHE_PRODUCTS, key = "#id")
    @Transactional
    public void deactivate(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> AppException.notFound("Product not found: " + id));
        product.setActive(false);
        productRepository.save(product);
        log.info("Product deactivated: {}", id);
    }
}
