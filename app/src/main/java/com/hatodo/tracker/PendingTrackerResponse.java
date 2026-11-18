package com.hatodo.tracker;

import com.hatodo.list.ListIcon;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A daily tracker with nothing recorded today (requirements/0019 R171).
 *
 * <p>Carries the bucket it is asking about, so the agenda's Add control opens
 * a form already pointed at the right day rather than recomputing "today" from
 * the device - which is the one thing the household's zone exists to stop.
 */
public record PendingTrackerResponse(UUID id, String name, ListIcon icon, String unit,
                                     short decimals, LocalDate periodStart) {
}
