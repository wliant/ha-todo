package com.hatodo.list;

import java.util.UUID;

/**
 * A list is about to be deleted, with everything on it.
 *
 * <p>Published <em>before</em> the delete, so a handler can still see what is
 * about to go. The database cascade removes the rows; it cannot remove the
 * bytes those rows point at, and once the rows are gone nothing knows the
 * bytes were ever there.
 */
public record ListDeletedEvent(UUID listId) {
}
