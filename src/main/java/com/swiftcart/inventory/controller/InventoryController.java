package com.swiftcart.inventory.controller;

import com.swiftcart.common.response.ApiResponse;
import com.swiftcart.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    /** GET /api/v1/inventory/{productId} */
    @GetMapping("/{productId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStock(@PathVariable Long productId) {
        return ResponseEntity.ok(ApiResponse.ok(inventoryService.getStock(productId)));
    }

    /** POST /api/v1/inventory/{productId}/restock — ADMIN only */
    @PostMapping("/{productId}/restock")
    public ResponseEntity<ApiResponse<Void>> restock(
            @PathVariable Long productId,
            @RequestParam int quantity) {
        inventoryService.restock(productId, quantity);
        return ResponseEntity.ok(ApiResponse.ok("Restocked successfully", null));
    }
}
