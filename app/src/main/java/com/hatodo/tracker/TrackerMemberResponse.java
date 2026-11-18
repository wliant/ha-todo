package com.hatodo.tracker;

import com.hatodo.user.AppUser;
import java.util.UUID;

/**
 * Someone who can see a tracker. Never carries anything about how they sign in
 * - the sign-in roster's rule (adr/0009) is about every surface, not only the
 * roster.
 */
public record TrackerMemberResponse(UUID id, String displayName, boolean owner,
                                    UUID avatarImageId) {

    public static TrackerMemberResponse of(AppUser user, boolean owner) {
        return new TrackerMemberResponse(user.getId(), user.getDisplayName(), owner,
                user.getAvatarImageId());
    }
}
