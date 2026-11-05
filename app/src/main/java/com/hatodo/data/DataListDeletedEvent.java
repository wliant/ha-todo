package com.hatodo.data;

import java.util.UUID;

/**
 * Announced before a collection's rows go, so whatever holds bytes outside the
 * database can forget them while the ids are still readable. The cascade takes
 * the {@code stored_image} rows with the entries, and after that nothing
 * remembers the pictures exist.
 */
public record DataListDeletedEvent(UUID listId) {
}
