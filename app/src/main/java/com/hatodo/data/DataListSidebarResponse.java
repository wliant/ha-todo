package com.hatodo.data;

import java.util.List;

/**
 * The sidebar's collections: the ones the member pinned, and the group below
 * them (0012 R98-R106).
 *
 * <p>Its own response from its own controller rather than components on
 * {@code SidebarResponse}: the two aggregates stay apart (adr/0029), and
 * nothing is computed from both - the shell merges the two pinned halves by
 * name for display, which is why both arrive ordered that way (adr/0042,
 * adr/0043).
 */
public record DataListSidebarResponse(List<DataListSummaryResponse> pinned,
                                      List<DataListSummaryResponse> collections) {
}
