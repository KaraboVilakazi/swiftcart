package com.swiftcart.user.dto;

import com.swiftcart.user.domain.User;

import java.time.Instant;

/**
 * Public-facing user projection — never exposes passwordHash.
 */
public record UserResponse(
    Long    id,
    String  email,
    String  firstName,
    String  lastName,
    String  role,
    Instant createdAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(
            user.getId(),
            user.getEmail(),
            user.getFirstName(),
            user.getLastName(),
            user.getRole().name(),
            user.getCreatedAt()
        );
    }
}
