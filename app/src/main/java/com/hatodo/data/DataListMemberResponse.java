package com.hatodo.data;

import com.hatodo.user.AppUser;
import java.util.UUID;

/**
 * Someone who can see a collection. Never carries anything about how they sign
 * in - the sign-in roster's rule (adr/0009) is about every surface, not only
 * the roster.
 */
public record DataListMemberResponse(UUID id, String displayName, boolean owner,
                                     UUID avatarImageId) {

    public static DataListMemberResponse of(AppUser user, boolean owner) {
        return new DataListMemberResponse(user.getId(), user.getDisplayName(), owner,
                user.getAvatarImageId());
    }
}
