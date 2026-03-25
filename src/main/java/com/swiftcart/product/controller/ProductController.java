package com.swiftcart.product.controller;

import com.swiftcart.common.response.ApiResponse;
import com.swiftcart.product.dto.CreateProductRequest;
import com.swiftcart.product.dto.ProductResponse;
import com.swiftcart.product.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    /** GET /api/v1/products?page=0&size=20&sort=name,asc */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<ProductResponse>>> listProducts(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(productService.listAll(pageable)));
    }

    /** GET /api/v1/products/search?q=samsung */
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<Page<ProductResponse>>> search(
            @RequestParam String q,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(productService.search(q, pageable)));
    }

    /** GET /api/v1/products/{id} */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductResponse>> getProduct(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(productService.getById(id)));
    }

    /** GET /api/v1/products/category/{categoryId} */
    @GetMapping("/category/{categoryId}")
    public ResponseEntity<ApiResponse<Page<ProductResponse>>> listByCategory(
            @PathVariable Long categoryId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(productService.listByCategory(categoryId, pageable)));
    }

    /** POST /api/v1/products  — ADMIN only (enforced in service via @PreAuthorize) */
    @PostMapping
    public ResponseEntity<ApiResponse<ProductResponse>> createProduct(
            @Valid @RequestBody CreateProductRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.created(productService.create(request)));
    }

    /** DELETE /api/v1/products/{id}  — soft delete */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deactivateProduct(@PathVariable Long id) {
        productService.deactivate(id);
        return ResponseEntity.ok(ApiResponse.ok("Product deactivated", null));
    }
}
