package com.hatodo.data;

import com.hatodo.list.ListIcon;
import java.util.UUID;

/**
 * A collection as a card on the collections page, or a row in the share
 * picker.
 *
 * <p>Carries {@code entryCount} rather than the entries: the index is a glance,
 * and R57 says a collection is read by looking things up.
 */
public record DataListSummaryResponse(UUID id, String name, String description, ListIcon icon,
                                      boolean owned, int memberCount, long entryCount) {
}
