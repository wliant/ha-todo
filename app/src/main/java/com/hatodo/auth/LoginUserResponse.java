package com.hatodo.auth;

import com.hatodo.user.AppUser;
import com.hatodo.user.UserRole;
import java.util.UUID;

/**
 * One entry in the pick-a-user sign-in list. This is served to anonymous
 * callers, so it carries only what is needed to draw that screen and must never
 * grow a field beyond it.
 */
public record LoginUserResponse(UUID id, String username, String displayName, UserRole role,
                                boolean passwordRequired) {

    public static LoginUserResponse of(AppUser user) {
        return new LoginUserResponse(user.getId(), user.getUsername(), user.getDisplayName(),
                user.getRole(), user.isPasswordRequired());
    }
}
