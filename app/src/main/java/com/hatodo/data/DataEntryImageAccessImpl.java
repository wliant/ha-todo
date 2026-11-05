package com.hatodo.data;

import com.hatodo.images.DataEntryImageAccess;
import com.hatodo.user.CurrentUserProvider;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * A picture in an entry's detail is readable by exactly the people its
 * collection is (requirements/0006 R61).
 */
@Component
public class DataEntryImageAccessImpl implements DataEntryImageAccess {

    private final DataEntryRepository entries;
    private final DataListService lists;
    private final CurrentUserProvider currentUser;

    public DataEntryImageAccessImpl(DataEntryRepository entries, DataListService lists,
                                    CurrentUserProvider currentUser) {
        this.entries = entries;
        this.lists = lists;
        this.currentUser = currentUser;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canReadEntry(UUID entryId) {
        // The image endpoint is permitAll so the sign-in screen can draw
        // avatars, which means an anonymous caller reaches here. For an entry's
        // picture the answer is simply no - asked before any transactional
        // call, because failing inside one is a 500 where a 404 was the point.
        if (currentUser.find().isEmpty()) {
            return false;
        }
        // isReadable rather than requireReadable: catching the exception from
        // the throwing one leaves the surrounding transaction rollback-only,
        // and the commit then fails with a 500.
        return entries.findWithList(entryId)
                .map(entry -> lists.isReadable(entry.getListId()))
                .orElse(false);
    }
}
