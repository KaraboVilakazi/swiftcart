package com.swiftcart.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftcart.cart.dto.CartResponse;
import com.swiftcart.common.response.ApiResponse;
import com.swiftcart.order.dto.PlaceOrderRequest;
import com.swiftcart.user.dto.AuthResponse;
import com.swiftcart.user.dto.LoginRequest;
import com.swiftcart.user.dto.RegisterRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end integration test for the full shopping flow:
 *   Register → Login → Browse products → Add to cart → Place order
 *
 * Uses Testcontainers to spin up a real PostgreSQL instance.
 * Flyway migrations run automatically, seeding products and categories.
 * Redis is mocked (cache disabled in test profile).
 *
 * This test catches regressions that unit tests can't — JPA mappings,
 * Flyway schema, transaction boundaries, and security filter chain.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Order Flow Integration Test")
class OrderFlowIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("swiftcart_test")
                    .withUsername("test")
                    .withPassword("test");

    @Autowired MockMvc       mockMvc;
    @Autowired ObjectMapper  objectMapper;

    // Shared state across ordered tests
    static String jwt;

    // ------------------------------------------------------------------ //
    // Step 1 — Register
    // ------------------------------------------------------------------ //

    @Test
    @org.junit.jupiter.api.Order(1)
    @DisplayName("1. Register a new customer")
    void step1_register() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "karabo@integration.test",
                "Password1!",
                "Karabo",
                "Vilakazi"
        );

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        AuthResponse auth = objectMapper.readValue(
                objectMapper.readTree(body).get("data").toString(), AuthResponse.class);

        jwt = auth.token();
        assertThat(jwt).isNotBlank();
    }

    // ------------------------------------------------------------------ //
    // Step 2 — Login
    // ------------------------------------------------------------------ //

    @Test
    @org.junit.jupiter.api.Order(2)
    @DisplayName("2. Login returns a valid JWT")
    void step2_login() throws Exception {
        LoginRequest request = new LoginRequest("karabo@integration.test", "Password1!");

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("karabo@integration.test"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        AuthResponse auth = objectMapper.readValue(
                objectMapper.readTree(body).get("data").toString(), AuthResponse.class);

        jwt = auth.token();
    }

    // ------------------------------------------------------------------ //
    // Step 3 — Browse products (public)
    // ------------------------------------------------------------------ //

    @Test
    @org.junit.jupiter.api.Order(3)
    @DisplayName("3. Products endpoint is publicly accessible")
    void step3_browseProducts() throws Exception {
        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.totalElements").value(6));
    }

    // ------------------------------------------------------------------ //
    // Step 4 — Add to cart
    // ------------------------------------------------------------------ //

    @Test
    @org.junit.jupiter.api.Order(4)
    @DisplayName("4. Authenticated user can add item to cart")
    void step4_addToCart() throws Exception {
        mockMvc.perform(post("/api/v1/cart/items")
                        .param("productId", "1")
                        .param("quantity", "2")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.itemCount").value(1))
                .andExpect(jsonPath("$.data.items[0].productId").value(1))
                .andExpect(jsonPath("$.data.items[0].quantity").value(2));
    }

    // ------------------------------------------------------------------ //
    // Step 5 — Place order
    // ------------------------------------------------------------------ //

    @Test
    @org.junit.jupiter.api.Order(5)
    @DisplayName("5. User can place an order from their cart")
    void step5_placeOrder() throws Exception {
        PlaceOrderRequest request = new PlaceOrderRequest("123 Sandton Drive, Johannesburg");

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.shippingAddress").value("123 Sandton Drive, Johannesburg"));
    }

    // ------------------------------------------------------------------ //
    // Step 6 — Cart is cleared after order
    // ------------------------------------------------------------------ //

    @Test
    @org.junit.jupiter.api.Order(6)
    @DisplayName("6. Cart is empty after successful order")
    void step6_cartClearedAfterOrder() throws Exception {
        mockMvc.perform(get("/api/v1/cart")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.itemCount").value(0));
    }

    // ------------------------------------------------------------------ //
    // Security checks
    // ------------------------------------------------------------------ //

    @Test
    @org.junit.jupiter.api.Order(7)
    @DisplayName("7. Cart endpoint returns 403 without JWT")
    void step7_cartRequiresAuth() throws Exception {
        mockMvc.perform(get("/api/v1/cart"))
                .andExpect(status().isForbidden());
    }

    @Test
    @org.junit.jupiter.api.Order(8)
    @DisplayName("8. Order endpoint returns 403 without JWT")
    void step8_orderRequiresAuth() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shippingAddress\":\"test\"}"))
                .andExpect(status().isForbidden());
    }
}
