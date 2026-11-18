package com.hatodo.tracker;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * Where a bucket starts (requirements/0019 R166, adr/0052).
 *
 * <p>Pure date arithmetic and therefore pure unit tests - but the cases below
 * are the ones that are wrong in production rather than the ones that are
 * obvious: a Sunday, a month end, and the turn of a year.
 */
class TrackerPeriodTest {

    @Test
    void aDayIsItsOwnBucket() {
        assertThat(TrackerPeriod.DAY.startOf(LocalDate.of(2026, 11, 19)))
                .isEqualTo(LocalDate.of(2026, 11, 19));
    }

    /**
     * The one everybody gets wrong, and the one that already bit
     * {@code test_dashboard.py}: a Sunday belongs to the week that began on the
     * <em>preceding</em> Monday, not the one starting tomorrow.
     */
    @Test
    void aSundayBelongsToTheWeekThatStartedOnTheMondayBefore() {
        // 2026-11-22 is a Sunday.
        assertThat(TrackerPeriod.WEEK.startOf(LocalDate.of(2026, 11, 22)))
                .isEqualTo(LocalDate.of(2026, 11, 16));
    }

    @Test
    void aMondayIsAlreadyTheStartOfItsWeek() {
        assertThat(TrackerPeriod.WEEK.startOf(LocalDate.of(2026, 11, 16)))
                .isEqualTo(LocalDate.of(2026, 11, 16));
    }

    @Test
    void anyDayOfAMonthBucketsToTheFirst() {
        assertThat(TrackerPeriod.MONTH.startOf(LocalDate.of(2026, 11, 30)))
                .isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(TrackerPeriod.MONTH.startOf(LocalDate.of(2026, 11, 1)))
                .isEqualTo(LocalDate.of(2026, 11, 1));
    }

    @Test
    void aWeekCanStartInThePreviousYear() {
        // 2027-01-01 is a Friday; its week began on 2026-12-28.
        assertThat(TrackerPeriod.WEEK.startOf(LocalDate.of(2027, 1, 1)))
                .isEqualTo(LocalDate.of(2026, 12, 28));
    }

    @Test
    void theNextBucketStepsByTheRightUnit() {
        assertThat(TrackerPeriod.DAY.next(LocalDate.of(2026, 11, 19)))
                .isEqualTo(LocalDate.of(2026, 11, 20));
        assertThat(TrackerPeriod.WEEK.next(LocalDate.of(2026, 11, 16)))
                .isEqualTo(LocalDate.of(2026, 11, 23));
        // 31 -> 30, which is where plusDays(31) would have been wrong.
        assertThat(TrackerPeriod.MONTH.next(LocalDate.of(2026, 1, 1)))
                .isEqualTo(LocalDate.of(2026, 2, 1));
        assertThat(TrackerPeriod.MONTH.next(LocalDate.of(2026, 12, 1)))
                .isEqualTo(LocalDate.of(2027, 1, 1));
    }

    /**
     * Folding can combine days into months. It cannot invent the days a
     * monthly tracker never recorded, which is why a finer grain is refused
     * rather than silently widened.
     */
    @Test
    void aSeriesCanBeDrawnCoarserButNeverFiner() {
        assertThat(TrackerPeriod.DAY.canBeDrawnAt(TrackerPeriod.MONTH)).isTrue();
        assertThat(TrackerPeriod.DAY.canBeDrawnAt(TrackerPeriod.DAY)).isTrue();
        assertThat(TrackerPeriod.MONTH.canBeDrawnAt(TrackerPeriod.DAY)).isFalse();
        assertThat(TrackerPeriod.WEEK.canBeDrawnAt(TrackerPeriod.DAY)).isFalse();
        assertThat(TrackerPeriod.WEEK.canBeDrawnAt(TrackerPeriod.MONTH)).isTrue();
    }
}
