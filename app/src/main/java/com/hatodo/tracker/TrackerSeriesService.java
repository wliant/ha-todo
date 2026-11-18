package com.hatodo.tracker;

import com.hatodo.household.HouseholdProperties;
import com.hatodo.user.AppUser;
import com.hatodo.user.CurrentUserProvider;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What the chart reads (requirements/0019 R163, R167).
 *
 * <p>The fold happens <b>here, in Java</b>, over a window capped at
 * {@link #MAX_READINGS}. {@code LATEST} in SQL wants {@code DISTINCT ON} and a
 * native query, and {@code date_trunc} would be a second implementation of
 * {@link TrackerPeriod#startOf} living in the database - adr/0044's reasoning,
 * where a household's data is not big enough to justify a second
 * implementation of a predicate. Hitting the cap is the threshold for
 * revisiting that, and nothing else.
 *
 * <p>One {@link Rollup} does both jobs: it folds the readings <em>inside</em> a
 * bucket, and it folds buckets into coarser ones when a long range is on
 * screen. Two implementations of that is how the chart and the headline come to
 * disagree.
 */
@Service
public class TrackerSeriesService {

    /** Five years of daily readings for four members. A bound, not a page. */
    static final int MAX_READINGS = 2000;

    /** How far back a series looks when the caller names no window. */
    private static final int DEFAULT_BUCKETS = 12;

    private final TrackerReadingRepository readings;
    private final TrackerService trackers;
    private final HouseholdProperties household;
    private final CurrentUserProvider currentUser;

    public TrackerSeriesService(TrackerReadingRepository readings, TrackerService trackers,
                                HouseholdProperties household, CurrentUserProvider currentUser) {
        this.readings = readings;
        this.trackers = trackers;
        this.household = household;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public TrackerSeriesResponse series(UUID trackerId, LocalDate from, LocalDate to,
                                        TrackerPeriod by) {
        currentUser.requireUserId();
        Tracker tracker = trackers.requireReadable(trackerId);
        TrackerPeriod grain = by == null ? tracker.getPeriod() : by;
        if (!tracker.getPeriod().canBeDrawnAt(grain)) {
            // Folding can combine days into months; it cannot invent the days a
            // monthly tracker never recorded.
            throw new UnsupportedGrainException(
                    "A " + tracker.getPeriod() + " tracker cannot be drawn by " + grain + ".");
        }

        // The window's defaults are the household's, never LocalDate.now() with
        // no zone - which is the container's, which is UTC, which puts a
        // Singapore evening on the wrong day (adr/0036).
        LocalDate today = LocalDate.now(household.zone());
        LocalDate end = grain.next(grain.startOf(to == null ? today : to));
        LocalDate start = grain.startOf(from == null ? backFrom(end, grain) : from);

        List<TrackerReading> rows =
                readings.findInWindow(trackerId, start, end, Limit.of(MAX_READINGS));

        List<LocalDate> buckets = bucketsBetween(start, end, grain);
        return new TrackerSeriesResponse(tracker.getId(), tracker.getUnit(), tracker.getPeriod(),
                tracker.getRollup(), tracker.getScope(), tracker.getDecimals(),
                tracker.getTarget(), tracker.getTargetDirection(), grain, start, end,
                buckets, fold(rows, buckets, grain, tracker));
    }

    private LocalDate backFrom(LocalDate end, TrackerPeriod grain) {
        LocalDate start = end;
        for (int i = 0; i < DEFAULT_BUCKETS; i++) {
            start = switch (grain) {
                case DAY -> start.minusDays(1);
                case WEEK -> start.minusWeeks(1);
                case MONTH -> start.minusMonths(1);
            };
        }
        return start;
    }

    private List<LocalDate> bucketsBetween(LocalDate start, LocalDate end, TrackerPeriod grain) {
        List<LocalDate> buckets = new ArrayList<>();
        for (LocalDate at = start; at.isBefore(end); at = grain.next(at)) {
            buckets.add(at);
        }
        return buckets;
    }

    /**
     * Rows into lines.
     *
     * <p>Grouped by series, then by bucket, then folded. A bucket with no
     * readings becomes {@code null} rather than zero (R167): a missed weigh-in
     * is not 0 kg, and the chart draws a break rather than a crash.
     *
     * <p>A {@code PER_MEMBER} tracker yields a line per member <em>with
     * readings</em>, not one per grantee - an empty line for somebody who has
     * never recorded is a legend entry that explains nothing.
     */
    private List<TrackerSeriesResponse.Series> fold(List<TrackerReading> rows,
                                                    List<LocalDate> buckets,
                                                    TrackerPeriod grain,
                                                    Tracker tracker) {
        // Insertion-ordered, and the rows arrive ordered by bucket then by
        // recordedAt - which is what lets LATEST simply take the last value of
        // the list it is handed.
        Map<UUID, AppUser> members = new LinkedHashMap<>();
        Map<UUID, Map<LocalDate, List<BigDecimal>>> bySeries = new LinkedHashMap<>();

        for (TrackerReading reading : rows) {
            UUID key = reading.getMemberId();
            members.putIfAbsent(key, reading.getMember());
            bySeries.computeIfAbsent(key, ignored -> new LinkedHashMap<>())
                    .computeIfAbsent(grain.startOf(reading.getPeriodStart()),
                            ignored -> new ArrayList<>())
                    .add(reading.getAmount());
        }

        List<TrackerSeriesResponse.Series> series = new ArrayList<>();
        for (Map.Entry<UUID, Map<LocalDate, List<BigDecimal>>> entry : bySeries.entrySet()) {
            AppUser member = members.get(entry.getKey());
            List<BigDecimal> values = new ArrayList<>(buckets.size());
            for (LocalDate bucket : buckets) {
                List<BigDecimal> inBucket = entry.getValue().get(bucket);
                values.add(inBucket == null ? null : tracker.getRollup().fold(inBucket));
            }
            series.add(new TrackerSeriesResponse.Series(
                    entry.getKey(),
                    member == null ? "Everyone" : member.getDisplayName(),
                    member == null ? null : member.getAvatarImageId(),
                    values));
        }
        return series;
    }
}
