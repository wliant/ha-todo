package com.hatodo.item;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * @param version echoed back on the next write, so a stale editor is told
 *                rather than quietly winning
 */
public record ItemResponse(UUID id, UUID listId, String description, String detailHtml,
                           ItemMemberResponse assignee, Instant dueAt,
                           boolean completed, ItemMemberResponse completedBy,
                           Instant completedAt, Integer pointsEarned,
                           List<ItemCostValueResponse> costValues, long version) {

    public static ItemResponse of(TodoItem item) {
        return new ItemResponse(item.getId(), item.getListId(), item.getDescription(),
                item.getDetailHtml(), ItemMemberResponse.of(item.getAssignee()),
                item.getDueAt(), item.isCompleted(),
                ItemMemberResponse.of(item.getCompletedBy()), item.getCompletedAt(),
                item.getPointsEarned(),
                item.getCostValues().stream().map(ItemCostValueResponse::of).toList(),
                item.getVersion());
    }
}
