package com.hatodo.item;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * @param selfAssigned the assignee put themselves there (R47). A boolean, not
 *                     the id behind it: the badge needs one bit, and publishing
 *                     who delegated to whom is a fact nothing asks for
 *                     (adr/0026).
 * @param version      echoed back on the next write, so a stale editor is told
 *                     rather than quietly winning
 */
public record ItemResponse(UUID id, UUID listId, String description, String detailHtml,
                           ItemMemberResponse assignee, boolean selfAssigned, Instant dueAt,
                           boolean completed, ItemMemberResponse completedBy,
                           Instant completedAt, String completionNoteHtml,
                           Instant reopenedAt, Integer pointsEarned,
                           List<ItemCostValueResponse> costValues, long version) {

    public static ItemResponse of(TodoItem item) {
        // The completion record survives a reopen in the database and stops
        // being shown (R49) - so all three parts of it are reported only while
        // the completion stands. Reporting a completedBy on an item whose
        // `completed` is false would contradict itself, and the row would draw
        // a "done by" chip on an open item.
        boolean standing = item.isCompleted();
        return new ItemResponse(item.getId(), item.getListId(), item.getDescription(),
                item.getDetailHtml(), ItemMemberResponse.of(item.getAssignee()),
                item.isSelfAssigned(), item.getDueAt(), standing,
                standing ? ItemMemberResponse.of(item.getCompletedBy()) : null,
                standing ? item.getCompletedAt() : null,
                standing ? item.getCompletionNoteHtml() : null,
                item.getReopenedAt(), item.getPointsEarned(),
                item.getCostValues().stream().map(ItemCostValueResponse::of).toList(),
                item.getVersion());
    }
}
