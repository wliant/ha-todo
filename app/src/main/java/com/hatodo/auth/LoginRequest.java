package com.hatodo.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code password} is optional: a user whose {@code password_hash} is null signs
 * in by being picked, and anything sent here for them is ignored.
 */
public record LoginRequest(@NotBlank @Size(max = 50) String username, String password) {}
