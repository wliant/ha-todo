package com.hatodo.tracker;

import com.hatodo.list.ListIcon;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One tracker, whole.
 *
 * <p>{@code reconfigurable} is the client's half of R161: period and scope are
 * fixed once a reading exists, and the settings screen renders them read-only
 * rather than offering a control that 409s. Computed here rather than inferred
 * from {@code readingCount} by each screen, so there is one answer.
 */
public record TrackerDetailResponse(UUID id, String name, String description, ListIcon icon,
                                    String unit, TrackerPeriod period, TrackerScope scope,
                                    Rollup rollup, short decimals,
                                    BigDecimal target, TargetDirection targetDirection,
                                    boolean owned, boolean pinned, boolean reconfigurable,
                                    long version, List<TrackerMemberResponse> members,
                                    Instant createdAt) {
}
