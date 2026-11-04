package com.hatodo.auth;

import com.hatodo.user.AppUser;
import java.util.UUID;

/**
 * One entry in the pick-a-user sign-in list.
 *
 * <p>Served to anonymous callers, so it says only that this person exists:
 * {@code displayName} to draw the row, {@code username} because the login POST
 * is keyed by it, {@code id} as the row key. It deliberately does <em>not</em>
 * carry {@code role} or whether a password is required - publishing which
 * account administers the household, and which accounts have no secret, is a
 * list of free passes sorted by name. See
 * docs/adr/0009-opaque-sign-in-roster.md; never add a field here.
 */
public record LoginUserResponse(UUID id, String username, String displayName) {

    public static LoginUserResponse of(AppUser user) {
        return new LoginUserResponse(user.getId(), user.getUsername(), user.getDisplayName());
    }
}
