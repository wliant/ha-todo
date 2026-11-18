package com.hatodo.tracker;

import com.hatodo.list.ListIcon;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * A tracker as a card on the trackers page, or a row in the sidebar.
 *
 * <p>Carries the declaration - unit, period, scope, rollup - because a card
 * that said only "Body Weight" would not tell a member which of their two
 * weight trackers this is, and because the card shows the latest figure in its
 * unit.
 *
 * <p>{@code pinned} is this caller's own pin, not a property of the tracker:
 * two members looking at the same card see different values for it.
 */
public record TrackerSummaryResponse(UUID id, String name, String description, ListIcon icon,
                                     String unit, TrackerPeriod period, TrackerScope scope,
                                     Rollup rollup, short decimals,
                                     BigDecimal target, TargetDirection targetDirection,
                                     boolean owned, boolean pinned, int memberCount,
                                     long readingCount) {
}
