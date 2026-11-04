package com.hatodo.admin;

import com.hatodo.user.AppUser;
import com.hatodo.user.UserRole;
import java.time.Instant;
import java.util.UUID;

/** A user as the admin screen sees them. Never carries the password hash. */
public record AdminUserResponse(UUID id, String username, String displayName, UserRole role,
                                boolean passwordRequired, boolean enabled, Instant createdAt) {

    public static AdminUserResponse of(AppUser user) {
        return new AdminUserResponse(user.getId(), user.getUsername(), user.getDisplayName(),
                user.getRole(), user.isPasswordRequired(), user.isEnabled(), user.getCreatedAt());
    }
}
