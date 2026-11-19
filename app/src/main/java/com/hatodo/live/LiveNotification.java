package com.hatodo.live;

import java.util.UUID;

/**
 * What goes down the wire, and almost nothing does.
 *
 * <p>This is a <strong>doorbell, not a second read API</strong>. The client's
 * entire response is to invalidate what it has cached and refetch through the
 * endpoints that check the grant; nothing on screen is drawn from here. So
 * there is deliberately no detail HTML, no assignee, no costs, no due date and
 * no version - a version in particular invites a write against a value that was
 * never read through a guarded endpoint.
 *
 * <p>The day this carries enough to render a row from is the day it has
 * acquired a second copy of the access guard. At that point adr/0055 is to be
 * <em>reopened</em> rather than extended.
 *
 * @param actorDisplayName who added it, or {@code null} when the scheduled job
 *                         did and nobody asked
 * @param byYou            whether <em>this recipient</em> is the member who
 *                         added it. Computed per recipient on the server, so
 *                         0021 R181 lives in one place and no client ever
 *                         compares user ids. Always false for a notification
 *                         the recurrence job caused, including to the member
 *                         who wrote the rule (R182).
 */
public record LiveNotification(String type, UUID itemId, UUID listId, String listName,
                               String description, String actorDisplayName, boolean byYou) {

    /** The only type so far. The field exists so a second one is additive. */
    public static final String ITEM_CREATED = "item.created";
}
