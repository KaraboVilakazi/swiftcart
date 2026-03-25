package com.swiftcart.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

import java.time.Instant;

/**
 * Uniform API envelope returned by every endpoint.
 *
 * <pre>
 * {
 *   "success": true,
 *   "message": "Product retrieved",
 *   "data": { ... },
 *   "timestamp": "2024-01-01T10:00:00Z"
 * }
 * </pre>
 *
 * The {@code data} field is omitted from JSON when null
 * so error responses stay clean.
 */
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private final boolean success;
    private final String  message;
    private final T       data;
    private final Instant timestamp = Instant.now();

    private ApiResponse(boolean success, String message, T data) {
        this.success = success;
        this.message = message;
        this.data    = data;
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, "Success", data);
    }

    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(true, message, data);
    }

    public static <T> ApiResponse<T> created(T data) {
        return new ApiResponse<>(true, "Created successfully", data);
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, message, null);
    }
}
