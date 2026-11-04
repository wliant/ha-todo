package com.hatodo.item;

import com.hatodo.images.ImageAccess;
import com.hatodo.list.TodoListService;
import com.hatodo.user.CurrentUserProvider;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** A detail picture is readable by exactly the people its item's list is. */
@Component
public class ItemImageAccess implements ImageAccess {

    private final TodoItemRepository items;
    private final TodoListService lists;
    private final CurrentUserProvider currentUser;

    public ItemImageAccess(TodoItemRepository items, TodoListService lists,
                           CurrentUserProvider currentUser) {
        this.items = items;
        this.lists = lists;
        this.currentUser = currentUser;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canReadItem(UUID itemId) {
        // The image endpoint is permitAll so the sign-in screen can draw
        // avatars, which means an anonymous caller reaches here. For a detail
        // picture the answer is simply no - asked before any transactional
        // call, because failing inside one is a 500 where a 404 was the point.
        if (currentUser.find().isEmpty()) {
            return false;
        }
        // isReadable rather than requireReadable: catching the exception from
        // the throwing one leaves the surrounding transaction marked
        // rollback-only, and the commit then fails with a 500 where a 404 was
        // the whole point.
        return items.findById(itemId)
                .map(item -> lists.isReadable(item.getListId()))
                .orElse(false);
    }
}
