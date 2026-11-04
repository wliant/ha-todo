package com.hatodo.item;

import com.hatodo.list.ListAccessRevokedEvent;
import com.hatodo.list.ListDeletedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * What the item package has to do when something happens to a list.
 *
 * <p>Both handlers run synchronously, inside the publishing transaction, so
 * the list change and its consequence either both happen or neither does.
 */
@Component
public class ListEventListener {

    private final TodoItemService items;

    public ListEventListener(TodoItemService items) {
        this.items = items;
    }

    /** R24: an item cannot stay assigned to somebody who cannot see its list. */
    @EventListener
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void onAccessRevoked(ListAccessRevokedEvent event) {
        items.unassignEverythingFrom(event.listId(), event.userId());
    }

    /**
     * The database cascade removes the items and their pictures' rows; only
     * this removes the bytes those rows pointed at.
     */
    @EventListener
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void onListDeleted(ListDeletedEvent event) {
        items.forgetPicturesOnList(event.listId());
    }
}
