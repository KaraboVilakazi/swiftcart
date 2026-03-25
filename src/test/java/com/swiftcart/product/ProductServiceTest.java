package com.swiftcart.product;

import com.swiftcart.common.exception.AppException;
import com.swiftcart.inventory.domain.Inventory;
import com.swiftcart.inventory.repository.InventoryRepository;
import com.swiftcart.product.domain.Category;
import com.swiftcart.product.domain.Product;
import com.swiftcart.product.dto.CreateProductRequest;
import com.swiftcart.product.dto.ProductResponse;
import com.swiftcart.product.repository.CategoryRepository;
import com.swiftcart.product.repository.ProductRepository;
import com.swiftcart.product.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductService")
class ProductServiceTest {

    @Mock ProductRepository   productRepository;
    @Mock CategoryRepository  categoryRepository;
    @Mock InventoryRepository inventoryRepository;

    @InjectMocks
    ProductService productService;

    private Category electronics;
    private Product  product;

    @BeforeEach
    void setUp() {
        electronics = Category.builder().id(1L).name("Electronics").slug("electronics").build();
        product = Product.builder()
                .id(1L).name("Samsung Galaxy S24").slug("samsung-galaxy-s24")
                .price(new BigDecimal("18999.00")).category(electronics).active(true).build();
    }

    @Nested
    @DisplayName("getById()")
    class GetById {

        @Test
        @DisplayName("returns product when found and active")
        void getById_found() {
            when(productRepository.findActiveByIdWithCategory(1L)).thenReturn(Optional.of(product));

            ProductResponse response = productService.getById(1L);

            assertThat(response.id()).isEqualTo(1L);
            assertThat(response.name()).isEqualTo("Samsung Galaxy S24");
            assertThat(response.price()).isEqualByComparingTo("18999.00");
            assertThat(response.categoryName()).isEqualTo("Electronics");
        }

        @Test
        @DisplayName("throws not found when product does not exist")
        void getById_notFound() {
            when(productRepository.findActiveByIdWithCategory(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> productService.getById(99L))
                    .isInstanceOf(AppException.class)
                    .hasMessageContaining("not found");
        }
    }

    @Nested
    @DisplayName("listAll()")
    class ListAll {

        @Test
        @DisplayName("returns paginated active products")
        void listAll_returnsPaginatedProducts() {
            PageRequest pageable = PageRequest.of(0, 20);
            Page<Product> page = new PageImpl<>(List.of(product), pageable, 1);
            when(productRepository.findAllByActiveTrue(pageable)).thenReturn(page);

            Page<ProductResponse> result = productService.listAll(pageable);

            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).name()).isEqualTo("Samsung Galaxy S24");
        }
    }

    @Nested
    @DisplayName("create()")
    class Create {

        @Test
        @DisplayName("creates product and inventory record")
        void create_success() {
            CreateProductRequest request = new CreateProductRequest(
                    "New Phone", "new-phone", "A phone", new BigDecimal("9999.00"), 1L, 50
            );

            when(productRepository.existsBySlug("new-phone")).thenReturn(false);
            when(categoryRepository.findById(1L)).thenReturn(Optional.of(electronics));
            when(productRepository.save(any())).thenAnswer(inv -> {
                Product p = inv.getArgument(0);
                return Product.builder().id(10L).name(p.getName()).slug(p.getSlug())
                        .price(p.getPrice()).category(p.getCategory()).active(true).build();
            });
            when(inventoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ProductResponse response = productService.create(request);

            assertThat(response.name()).isEqualTo("New Phone");
            assertThat(response.categoryName()).isEqualTo("Electronics");
            verify(inventoryRepository).save(any(Inventory.class));
        }

        @Test
        @DisplayName("throws conflict when slug already exists")
        void create_conflictOnDuplicateSlug() {
            CreateProductRequest request = new CreateProductRequest(
                    "Duplicate", "samsung-galaxy-s24", "desc", new BigDecimal("100"), null, 10
            );
            when(productRepository.existsBySlug("samsung-galaxy-s24")).thenReturn(true);

            assertThatThrownBy(() -> productService.create(request))
                    .isInstanceOf(AppException.class)
                    .hasMessageContaining("Slug already in use");

            verify(productRepository, never()).save(any());
        }

        @Test
        @DisplayName("throws not found when category does not exist")
        void create_categoryNotFound() {
            CreateProductRequest request = new CreateProductRequest(
                    "Phone", "phone-slug", "desc", new BigDecimal("100"), 99L, 10
            );
            when(productRepository.existsBySlug("phone-slug")).thenReturn(false);
            when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> productService.create(request))
                    .isInstanceOf(AppException.class)
                    .hasMessageContaining("Category not found");
        }
    }
}
