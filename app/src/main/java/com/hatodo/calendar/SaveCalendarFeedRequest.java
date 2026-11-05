package com.hatodo.calendar;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * Subscribing a calendar, or replacing what one holds.
 *
 * @param url       null on a PATCH means "leave the address alone" - which is
 *                  the only way to edit a feed, since the stored address is
 *                  never readable back for the client to send again
 * @param memberIds who may see the events. Null on a PATCH means "leave the
 *                  audience alone"; an empty list means "nobody but me", which
 *                  is genuinely different and is what a new feed starts as
 */
public record SaveCalendarFeedRequest(
        @NotBlank @Size(max = 80) String label,
        @Size(max = 2000) String url,
        List<UUID> memberIds) {
}
