package com.hatodo.tracker;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

/**
 * The bucket a reading falls in (requirements/0019 R159, R166).
 *
 * <p>This enum is the <strong>one</strong> implementation of where a bucket
 * starts. The service canonicalises every incoming date through it, the series
 * folds through it, and the client mirrors it in {@code trackers/period.ts} -
 * but the server is the authority, so a client that sends the 18th of a monthly
 * tracker gets the 1st stored.
 *
 * <p>A week starts <strong>Monday</strong>, because
 * {@code web/src/dashboard/calendar.ts:startOfWeek} already does. A week
 * measured one way in the sidebar and another on a chart is adr/0036's "two
 * definitions of Thursday" wearing a new face.
 *
 * <p>Ordered coarsest-last on purpose: {@link #isCoarserThanOrEqualTo} reads
 * the declaration order, which is what refuses to draw a monthly tracker by
 * day (folding cannot invent days).
 */
public enum TrackerPeriod {
    DAY,
    WEEK,
    MONTH;

    /** The first day of the bucket the given household date falls in. */
    public LocalDate startOf(LocalDate date) {
        return switch (this) {
            case DAY -> date;
            case WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTH -> date.withDayOfMonth(1);
        };
    }

    /** The start of the bucket after the one beginning here. */
    public LocalDate next(LocalDate start) {
        return switch (this) {
            case DAY -> start.plusDays(1);
            case WEEK -> start.plusWeeks(1);
            case MONTH -> start.plusMonths(1);
        };
    }

    /**
     * Whether a series declared at this period can be drawn at {@code other}.
     *
     * <p>Coarser is fine - a year of a daily tracker is drawn by month - and
     * finer is not, because folding cannot invent the days that were never
     * recorded.
     */
    public boolean canBeDrawnAt(TrackerPeriod other) {
        return other.ordinal() >= ordinal();
    }
}
