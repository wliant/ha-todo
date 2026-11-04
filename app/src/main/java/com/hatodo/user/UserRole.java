package com.hatodo.user;

/**
 * Exactly two roles, and exactly one ADMIN row - the latter enforced by the
 * partial unique index ux_app_user_single_admin rather than by code.
 */
public enum UserRole {
    ADMIN,
    MEMBER
}
