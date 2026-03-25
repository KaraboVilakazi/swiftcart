package com.swiftcart.user.domain;

/**
 * User roles. Stored as a string in the DB so adding new roles
 * doesn't require a schema migration.
 */
public enum Role {
    CUSTOMER,
    ADMIN
}
