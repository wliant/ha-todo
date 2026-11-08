package com.hatodo.data;

import com.hatodo.list.ListIcon;
import java.util.UUID;

/**
 * A collection as a card on the collections page, or a row in the share
 * picker.
 *
 * <p>Carries {@code entryCount} rather than the entries: the index is a glance,
 * and R57 says a collection is read by looking things up.
 *
 * <p>{@code pinned} is this caller's own pin (0012 R104), not a property of the
 * collection - two members looking at the same card see different values for
 * it.
 */
public record DataListSummaryResponse(UUID id, String name, String description, ListIcon icon,
                                      boolean owned, boolean pinned, int memberCount,
                                      long entryCount) {
}
