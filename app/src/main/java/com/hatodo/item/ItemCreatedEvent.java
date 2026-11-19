package com.hatodo.item;

import java.util.UUID;

/**
 * An item was added to a list.
 *
 * <p>Published rather than delivered directly, because what reacts to it - a
 * notification on somebody else's open agenda - lives in the {@code live}
 * package, which depends on this one. An event keeps that arrow pointing one
 * way, as {@link com.hatodo.list.ListAccessRevokedEvent} does.
 *
 * <p><strong>Every component is a value, never an entity.</strong> The handler
 * runs after the transaction has committed and {@code spring.jpa.open-in-view}
 * is false, so reaching through to {@code item.getList().getName()} there
 * throws {@code LazyInitializationException} - and in the
 * {@link TodoItemService#createFromRule} case that throw lands inside the
 * recurrence runner's catch, is recorded against the rule, and reads as a
 * recurrence bug.
 *
 * @param actorId          who added it. Still populated for
 *                         {@link Source#RECURRENCE}, because the rule's author
 *                         is a real person and the audit trail says so - but
 *                         see {@link Source} for why that must not be read as
 *                         "they did this".
 * @param actorDisplayName their name, snapshotted here for the same reason the
 *                         list's is
 */
public record ItemCreatedEvent(UUID itemId, UUID listId, String listName,
                               String description, UUID actorId,
                               String actorDisplayName, Source source) {

    /**
     * Who the creation is attributable to, which is <em>not</em> the same
     * question as which user row the actor column holds.
     *
     * <p>{@link #RECURRENCE} exists because
     * {@link TodoItemService#createFromRule} takes the rule's author as its
     * actor - deliberately, since a synthetic system user would be a lie in the
     * audit trail (adr/0025). Without this discriminator, "stay quiet about my
     * own additions" would silence the 01:05 run for precisely the member who
     * wrote the standing instruction, which is 0021 R182 exactly backwards and
     * fails nothing. See adr/0055.
     */
    public enum Source {
        /** A member pressed Add. */
        MEMBER,
        /** The scheduled job materialised an occurrence. Nobody asked. */
        RECURRENCE
    }
}
