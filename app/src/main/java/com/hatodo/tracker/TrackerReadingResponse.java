package com.hatodo.tracker;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One reading, as the table draws it.
 *
 * <p>{@code memberId} is null for the household's series, which is the same
 * fact the column carries - there is no second representation to disagree with
 * it.
 *
 * <p>{@code editable} is computed for <strong>this</strong> caller by
 * {@link TrackerReading#isWritableBy}, so the client renders the flag and never
 * recomputes the rule from the scope plus the signed-in member. A second copy
 * of that predicate is how somebody gets an edit control that 403s, or - worse,
 * because nothing fails anywhere - no control on a number they may fix.
 */
public record TrackerReadingResponse(UUID id, UUID trackerId, UUID memberId,
                                     String memberDisplayName, UUID memberAvatarImageId,
                                     LocalDate periodStart, BigDecimal amount, String note,
                                     String recordedByDisplayName, Instant recordedAt,
                                     boolean editable, long version) {

    public static TrackerReadingResponse of(TrackerReading reading, UUID caller) {
        return new TrackerReadingResponse(
                reading.getId(),
                reading.getTrackerId(),
                reading.getMemberId(),
                reading.getMember() == null ? null : reading.getMember().getDisplayName(),
                reading.getMember() == null ? null : reading.getMember().getAvatarImageId(),
                reading.getPeriodStart(),
                reading.getAmount(),
                reading.getNote(),
                reading.getRecordedBy() == null ? null : reading.getRecordedBy().getDisplayName(),
                reading.getRecordedAt(),
                reading.isWritableBy(caller),
                reading.getVersion());
    }
}
