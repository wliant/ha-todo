package com.hatodo.dashboard;

import com.hatodo.item.ItemCostValueResponse;
import com.hatodo.item.ItemMemberResponse;
import com.hatodo.item.TodoItem;
import com.hatodo.list.ListIcon;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One item on the dashboard: everything an item response carries, plus which
 * list it came from.
 *
 * <p>The dashboard crosses lists, so every row has to say where it is from -
 * and the icon is how a list is recognised at a glance, which is R18's own
 * argument for requiring one.
 *
 * <p>It carries the whole payload even though the week and month views show
 * only a description, because the popup has to open instantly on a phone and
 * the alternative is a request per tap. At household volume a month is a few
 * dozen rows, and the pictures are {@code /api/images/{id}} references rather
 * than bytes.
 */
public record DashboardItemResponse(
        UUID id, UUID listId, String listName, ListIcon listIcon,
        String description, String detailHtml,
        ItemMemberResponse assignee, boolean selfAssigned, Instant dueAt,
        List<ItemCostValueResponse> costValues, long version) {

    static DashboardItemResponse of(TodoItem item) {
        return new DashboardItemResponse(item.getId(), item.getListId(),
                item.getList().getName(), item.getList().getIcon(),
                item.getDescription(), item.getDetailHtml(),
                ItemMemberResponse.of(item.getAssignee()), item.isSelfAssigned(),
                item.getDueAt(),
                item.getCostValues().stream().map(ItemCostValueResponse::of).toList(),
                item.getVersion());
    }
}
