package com.hatodo.calendar;

import com.hatodo.user.AppUser;
import java.util.UUID;

/**
 * Whose calendar an event came from.
 *
 * <p>Always shown, because "dinner at seven" means something different
 * depending on whose diary it is in - and because a member who can see that one
 * of their own feeds is the source can tell what they are publishing.
 */
public record CalendarEventSourceResponse(UUID feedId, String label, UUID memberId,
                                          String memberDisplayName, UUID avatarImageId) {

    static CalendarEventSourceResponse of(CalendarFeed feed) {
        AppUser owner = feed.getOwner();
        return new CalendarEventSourceResponse(feed.getId(), feed.getLabel(), owner.getId(),
                owner.getDisplayName(), owner.getAvatarImageId());
    }
}
