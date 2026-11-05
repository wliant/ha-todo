package com.hatodo.list;

import com.hatodo.user.AppUser;
import java.util.UUID;

/** Someone who can see a list. Never carries anything about how they sign in. */
public record ListMemberResponse(UUID id, String displayName, boolean owner,
                                 UUID avatarImageId) {

    public static ListMemberResponse of(AppUser user, boolean owner) {
        return new ListMemberResponse(user.getId(), user.getDisplayName(), owner,
                user.getAvatarImageId());
    }
}
