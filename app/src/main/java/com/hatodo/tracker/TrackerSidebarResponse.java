package com.hatodo.tracker;

import java.util.List;

/**
 * The sidebar's trackers: the pinned ones, then the group.
 *
 * <p>The two <strong>partition</strong> what the member can see - the group
 * query carries {@code WHERE s.pinned_at IS NULL} - so nothing is drawn twice
 * and pinning twelve things with a preference of ten still leaves a group
 * underneath them.
 */
public record TrackerSidebarResponse(List<TrackerSummaryResponse> pinned,
                                     List<TrackerSummaryResponse> trackers) {
}
