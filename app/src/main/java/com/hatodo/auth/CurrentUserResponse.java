package com.hatodo.auth;

import com.hatodo.user.AppUser;
import com.hatodo.user.UserRole;
import java.time.ZoneId;
import java.util.UUID;

/**
 * @param timezone the household's zone, not this account's. It rides the
 *                 session's own payload so the SPA never has to guess, and
 *                 never reads the device's zone - which would disagree with
 *                 every other member the moment somebody travels. See
 *                 docs/adr/0036.
 */
public record CurrentUserResponse(UUID id, String username, String displayName, UserRole role,
                                  UUID avatarImageId, String timezone) {

    public static CurrentUserResponse of(AppUser user, ZoneId household) {
        return new CurrentUserResponse(user.getId(), user.getUsername(), user.getDisplayName(),
                user.getRole(), user.getAvatarImageId(), household.getId());
    }
}
