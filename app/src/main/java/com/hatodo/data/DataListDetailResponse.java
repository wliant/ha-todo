package com.hatodo.data;

import com.hatodo.list.ListIcon;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** One collection, with its audience and the version a writer must echo back. */
public record DataListDetailResponse(UUID id, String name, String description, ListIcon icon,
                                     boolean owned, long version,
                                     List<DataListMemberResponse> members, Instant createdAt) {
}
