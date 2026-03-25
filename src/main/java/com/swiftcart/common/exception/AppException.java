package com.swiftcart.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Base application exception.
 *
 * Carrying the HTTP status here keeps the exception semantics
 * close to the error and lets the GlobalExceptionHandler stay
 * simple — it just reads the status from the exception.
 */
@Getter
public class AppException extends RuntimeException {

    private final HttpStatus status;

    public AppException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    // ------------------------------------------------------------------ //
    // Convenience factories — readable at the call site                    //
    // ------------------------------------------------------------------ //

    public static AppException notFound(String message) {
        return new AppException(message, HttpStatus.NOT_FOUND);
    }

    public static AppException badRequest(String message) {
        return new AppException(message, HttpStatus.BAD_REQUEST);
    }

    public static AppException conflict(String message) {
        return new AppException(message, HttpStatus.CONFLICT);
    }

    public static AppException forbidden(String message) {
        return new AppException(message, HttpStatus.FORBIDDEN);
    }

    public static AppException unprocessable(String message) {
        return new AppException(message, HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
