package com.hatodo.auth;

import com.hatodo.user.AppUser;
import com.hatodo.user.UserRole;
import java.util.UUID;

public record CurrentUserResponse(UUID id, String username, String displayName, UserRole role) {

    public static CurrentUserResponse of(AppUser user) {
        return new CurrentUserResponse(user.getId(), user.getUsername(), user.getDisplayName(), user.getRole());
    }
}
