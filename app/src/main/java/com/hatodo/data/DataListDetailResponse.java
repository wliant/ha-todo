package com.hatodo.data;

import com.hatodo.list.ListIcon;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One collection, with its audience and the version a writer must echo back.
 *
 * <p>{@code pinned} is the caller's own pin (0012 R104), so the detail screen
 * can draw the star without a second request.
 */
public record DataListDetailResponse(UUID id, String name, String description, ListIcon icon,
                                     boolean owned, boolean pinned, long version,
                                     List<DataListMemberResponse> members, Instant createdAt) {
}
