package com.hatodo.list;

import java.util.UUID;

/** A list as a card on the all-lists page, or a row in the sidebar. */
public record ListSummaryResponse(UUID id, String name, String description, ListIcon icon,
                                  boolean owned, boolean pinned, int memberCount) {
}
