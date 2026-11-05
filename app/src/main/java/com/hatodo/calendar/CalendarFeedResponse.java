package com.hatodo.calendar;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One of the caller's feeds.
 *
 * <p><b>There is no component for the address, and there must never be one.</b>
 * A feed URL is a bearer capability for somebody's whole calendar; the absence
 * of a field is a stronger guarantee than remembering not to populate one, and
 * {@code CalendarFeedServiceTest} asserts over this record's components so that
 * adding one fails a test rather than leaking a calendar. See docs/adr/0035.
 *
 * @param memberIds who else may see it. The owner is not listed: they always
 *                  can, and a list that sometimes contains the caller is a
 *                  checkbox that sometimes ticks itself.
 * @param stale     whether the member should be told the events may be missing
 *                  (R71) - computed against app.calendar.stale-after rather
 *                  than left for the client to work out
 */
public record CalendarFeedResponse(UUID id, String label, CalendarProvider provider,
                                   List<UUID> memberIds, Instant lastSuccessAt, String lastError,
                                   boolean stale, long eventCount) {
}
