package com.hatodo.live;

import com.hatodo.item.ItemCreatedEvent;
import com.hatodo.list.ListAccessRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Who gets told that a chore arrived (0021 R179, R183).
 *
 * <p><strong>Routing, not broadcasting.</strong> The recipients are resolved
 * here and written to individually; there is no channel anybody subscribes to
 * and no filtering on the client. The cheap alternative - broadcast and let
 * each browser ignore what is not theirs - passes every test that could be
 * written against the UI and puts the description of every private list in the
 * household into every connected browser. See adr/0055.
 */
@ConditionalOnProperty(name = "app.live.enabled", havingValue = "true", matchIfMissing = true)
@Component
public class ItemCreatedListener {

    private static final Logger log = LoggerFactory.getLogger(ItemCreatedListener.class);

    private final ListAccessRepository access;
    private final LiveBroker broker;

    public ItemCreatedListener(ListAccessRepository access, LiveBroker broker) {
        this.access = access;
        this.broker = broker;
    }

    /**
     * <strong>AFTER_COMMIT, never a plain {@code @EventListener}.</strong>
     * Notifying inline tells a browser to refetch a row Postgres has not made
     * visible yet: the message names a chore the agenda does not show. It
     * <em>self-heals on the next refetch</em>, so nothing fails and no test
     * catches it - the report is "I saw a message about a chore that wasn't
     * there". {@code ItemCreatedListenerTest} asserts this annotation
     * reflectively, because there is nothing else that could.
     *
     * <p>{@code REQUIRES_NEW}: the publishing transaction has already
     * committed, so the recipient query needs one of its own rather than
     * joining a transaction that is finished.
     *
     * <p>Everything is caught. This runs on the publishing thread after the
     * commit, so a throw here turns a successful create into a 500 on a row
     * that <em>was</em> saved. Notification is best-effort by definition; the
     * item is the transaction's product and it is already safe.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onItemCreated(ItemCreatedEvent event) {
        try {
            broker.send(notifications(event));
        } catch (RuntimeException failed) {
            log.warn("could not announce item {}", event.itemId(), failed);
        }
    }

    private Map<UUID, LiveNotification> notifications(ItemCreatedEvent event) {
        List<UUID> recipients = access.findUserIdsForList(event.listId());
        Map<UUID, LiveNotification> perRecipient = new HashMap<>();
        for (UUID recipient : recipients) {
            perRecipient.put(recipient, new LiveNotification(
                    LiveNotification.ITEM_CREATED,
                    event.itemId(),
                    event.listId(),
                    event.listName(),
                    event.description(),
                    attribution(event),
                    byYou(event, recipient)));
        }
        return perRecipient;
    }

    /**
     * Whether <em>this</em> recipient is the one who added it.
     *
     * <p>A RECURRENCE event is never anybody's own act, even for the member
     * whose name is on the rule. {@code createFromRule}'s actor is its author -
     * a real person, because a synthetic system user would be a lie in the
     * audit trail - so without the discriminator the one member guaranteed to
     * hear nothing at 01:05 is the one who wrote the standing instruction.
     * That is R182 exactly backwards, and nothing would fail.
     */
    private boolean byYou(ItemCreatedEvent event, UUID recipient) {
        return event.source() == ItemCreatedEvent.Source.MEMBER
                && recipient.equals(event.actorId());
    }

    /** Null when the job did it: nobody added this, so nobody is named. */
    private String attribution(ItemCreatedEvent event) {
        return event.source() == ItemCreatedEvent.Source.MEMBER
                ? event.actorDisplayName()
                : null;
    }
}
