package com.hatodo.auth;

import com.hatodo.user.AppUser;
import java.util.UUID;

/**
 * One entry in the pick-a-user sign-in list.
 *
 * <p>Served to anonymous callers, so it says only that this person exists:
 * {@code displayName} to draw the row, {@code username} because the login POST
 * is keyed by it, {@code id} as the row key, and {@code avatarImageId} so a
 * member who cannot yet read can pick their own face.
 *
 * <p>It deliberately does <em>not</em> carry {@code role} or whether a password
 * is required - publishing which account administers the household, and which
 * accounts have no secret, is a list of free passes sorted by name
 * (docs/adr/0009). The avatar is the single exception ever made to that rule,
 * argued in docs/adr/0019: unlike those two it says nothing about what an
 * account is worth attacking for. Nothing else goes here.
 */
public record LoginUserResponse(UUID id, String username, String displayName,
                                UUID avatarImageId) {

    public static LoginUserResponse of(AppUser user) {
        return new LoginUserResponse(user.getId(), user.getUsername(), user.getDisplayName(),
                user.getAvatarImageId());
    }
}
