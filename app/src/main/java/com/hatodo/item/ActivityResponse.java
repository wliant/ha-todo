package com.hatodo.item;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Who did what on one list, over a window (R43).
 *
 * <p>A separate thing from the all-time leaderboard: "this week" and "ever"
 * are different questions and a household asks both, so folding them together
 * would make one response mean two things and force the standing to refetch
 * whenever the window moved.
 *
 * @param members the window's totals per member, the header strip
 * @param events  the completions themselves, newest first - "who did what" is
 *                literally a feed, so this is the primary payload
 */
public record ActivityResponse(Instant from, Instant to,
                               List<Member> members, List<Event> events) {

    /** @param points may be lower than {@code completed} suggests: a reopened
     *                completion still happened, and still shows, but its points
     *                went back (R49) */
    public record Member(UUID userId, String displayName, UUID avatarImageId,
                         long completed, long points) {}

    /** @param reopened the work was later undone, which is worth seeing rather
     *                  than quietly dropping from the week */
    public record Event(UUID itemId, String description, Instant completedAt,
                        ItemMemberResponse completedBy, Integer pointsEarned,
                        boolean reopened) {

        static Event of(TodoItem item) {
            // "No longer standing", not "has a reopen stamp". Since 0015 a
            // lapse clears that stamp (it is what makes the item terminal
            // again), so an item completed, reopened and then lapsed has a
            // null reopened_at and a completion that very much was undone -
            // which the older test reported as still standing. This query only
            // ever selects rows with a completedAt, so the negation is exact.
            return new Event(item.getId(), item.getDescription(), item.getCompletedAt(),
                    ItemMemberResponse.of(item.getCompletedBy()), item.getPointsEarned(),
                    !item.isCompleted());
        }
    }
}
