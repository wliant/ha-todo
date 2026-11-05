package com.hatodo.data;

import com.hatodo.list.ListIcon;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One search result.
 *
 * <p>Carries the collection's name and icon, because R54 says a global result
 * has to say where it came from - and carrying them here is what keeps that
 * from being a lookup per row.
 *
 * <p>No detail: a result list is a list of names, and the detail arrives when
 * the member opens the entry (R57).
 */
public record DataEntryHit(UUID id, UUID dataListId, String listName, ListIcon listIcon,
                           String name, String description, List<String> tags,
                           long version, Instant updatedAt) {
}
