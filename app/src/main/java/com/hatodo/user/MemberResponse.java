package com.hatodo.user;

import java.util.UUID;

/**
 * A household member as the pickers see them - granting access to a list, and
 * later assigning an item.
 *
 * <p>Served from an authenticated endpoint, which is why it is not
 * {@code LoginUserResponse}: that one is permitAll and adr/0009 forbids growing
 * it. Even so this carries only what a picker draws, and nothing about how
 * anyone signs in.
 */
public record MemberResponse(UUID id, String displayName) {

    public static MemberResponse of(AppUser user) {
        return new MemberResponse(user.getId(), user.getDisplayName());
    }
}
