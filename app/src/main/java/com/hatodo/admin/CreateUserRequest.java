package com.hatodo.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * A blank or absent {@code password} means this user needs none and signs in by
 * being picked. The admin chooses that per user (requirements/0002 R10).
 */
public record CreateUserRequest(
        @NotBlank
        @Size(max = 50)
        @Pattern(regexp = "[A-Za-z0-9._-]+",
                 message = "may contain only letters, digits, dot, underscore and hyphen")
        String username,

        @NotBlank @Size(max = 100) String displayName,

        @Size(max = 100) String password) {

    public boolean wantsPassword() {
        return password != null && !password.isBlank();
    }
}
