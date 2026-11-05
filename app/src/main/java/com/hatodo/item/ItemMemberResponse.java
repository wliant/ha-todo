package com.hatodo.item;

import com.hatodo.user.AppUser;
import java.util.UUID;

/** Whoever an item names - its assignee, or whoever completed it. */
public record ItemMemberResponse(UUID id, String displayName, UUID avatarImageId) {

    public static ItemMemberResponse of(AppUser user) {
        return user == null ? null
                : new ItemMemberResponse(user.getId(), user.getDisplayName(),
                        user.getAvatarImageId());
    }
}
