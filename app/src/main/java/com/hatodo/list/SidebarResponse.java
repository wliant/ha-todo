package com.hatodo.list;

import java.util.List;

/**
 * Both sidebar sections in one request, because the shell draws them together
 * and two round trips would make them disagree while one was in flight.
 */
public record SidebarResponse(List<ListSummaryResponse> pinned,
                              List<ListSummaryResponse> recent) {
}
