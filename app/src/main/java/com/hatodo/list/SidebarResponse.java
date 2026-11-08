package com.hatodo.list;

import java.util.List;

/**
 * The sidebar's lists: the ones the member pinned, and the group below them
 * (0012 R98-R106).
 *
 * <p>Two components rather than one, because the two are drawn as two sections
 * and they partition the member's lists between them - {@code lists} excludes
 * everything in {@code pinned}, so nothing appears twice. One request, because
 * a split that could disagree while one half was in flight would show a list in
 * both places or neither.
 *
 * <p>{@code pinned} is uncapped; {@code lists} is capped at the member's
 * preference. A pin is an explicit instruction, so the section that holds them
 * scrolls rather than truncating (R106).
 */
public record SidebarResponse(List<ListSummaryResponse> pinned,
                              List<ListSummaryResponse> lists) {
}
