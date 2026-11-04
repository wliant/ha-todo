package com.hatodo.item;

import com.hatodo.list.ListAccessRevokedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps R24 true when a grant goes away: an item cannot stay assigned to
 * somebody who can no longer see the list it is on.
 *
 * <p>Synchronous and inside the publishing transaction, so revoking access and
 * unassigning either both happen or neither does.
 */
@Component
public class ListAccessRevokedListener {

    private final TodoItemService items;

    public ListAccessRevokedListener(TodoItemService items) {
        this.items = items;
    }

    @EventListener
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void onAccessRevoked(ListAccessRevokedEvent event) {
        items.unassignEverythingFrom(event.listId(), event.userId());
    }
}
