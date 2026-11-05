package com.hatodo.recurrence;

import com.hatodo.list.ListAccessRevokedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * What has to happen to a list's standing instructions when the list changes
 * underneath them.
 *
 * <p>In this package rather than beside the item listener, so {@code item} does
 * not acquire a dependency on {@code recurrence} - the dependency runs one way.
 *
 * <p>MANDATORY, like its siblings: inside the publishing transaction, so the
 * list change and its consequence either both happen or neither does.
 */
@Component
class RecurrenceEventListener {

    private final RecurrenceRuleService rules;

    RecurrenceEventListener(RecurrenceRuleService rules) {
        this.rules = rules;
    }

    /**
     * A rule outlives a grant, and this is what stops that becoming a quiet
     * failure every night: without it the rule keeps naming somebody who can no
     * longer see the list, and each run either refuses or produces work for a
     * member who cannot look at it (R24).
     */
    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void onAccessRevoked(ListAccessRevokedEvent event) {
        rules.unassignEverythingFrom(event.listId(), event.userId());
    }

    /*
     * Deliberately no onListDeleted here.
     *
     * A rule's pictures are already let go by the item listener, whose
     * StoredImageRepository.findForList is a native query spanning rule-owned
     * rows as well as item-owned ones. Loading RecurrenceRule entities during a
     * list's deletion is actively wrong: the TodoList they point at has already
     * been removed from the session, so the flush fails its transient-reference
     * check and deleting any list with a rule on it answers 500. That is not a
     * hypothetical - it is what the first local run of this feature did.
     */
}
