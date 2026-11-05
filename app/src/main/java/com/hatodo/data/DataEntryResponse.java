package com.hatodo.data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One entry in full, detail included.
 *
 * <p>{@code detailText} is deliberately absent: it exists to be indexed, not
 * read, and sending both would invite a client to render the wrong one.
 */
public record DataEntryResponse(UUID id, UUID dataListId, String name, String description,
                                String detailHtml, List<String> tags, long version,
                                UUID createdById, String createdByName,
                                Instant createdAt, Instant updatedAt) {

    public static DataEntryResponse of(DataEntry entry) {
        return new DataEntryResponse(entry.getId(), entry.getListId(), entry.getName(),
                entry.getDescription(), entry.getDetailHtml(), entry.getTags(),
                entry.getVersion(),
                entry.getCreatedBy() == null ? null : entry.getCreatedBy().getId(),
                entry.getCreatedBy() == null ? null : entry.getCreatedBy().getDisplayName(),
                entry.getCreatedAt(), entry.getUpdatedAt());
    }
}
