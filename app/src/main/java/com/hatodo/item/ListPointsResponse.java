package com.hatodo.item;

import java.util.UUID;

/** One member's standing on one list (R23). */
public record ListPointsResponse(UUID userId, String displayName, long points) {
}
