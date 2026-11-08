package com.hatodo.list;

import java.util.List;

/**
 * The sidebar's lists, in the order it draws them (0012 R98-R102).
 *
 * <p>One group, not the Pinned/Recent pair this record used to carry. A pin
 * sorts first inside it (R101) rather than heading a section of its own, which
 * is what lets the group be capped at the member's preference without a pin
 * being pushed out by it. Changing the record rather than adding a field is
 * deliberate: every reader of the old shape breaks at compile time on both
 * sides (adr/0042).
 */
public record SidebarResponse(List<ListSummaryResponse> lists) {
}
