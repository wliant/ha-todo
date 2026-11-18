package com.hatodo.tracker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.hatodo.household.HouseholdProperties;
import com.hatodo.list.ListIcon;
import com.hatodo.user.AppUser;
import com.hatodo.user.AppUserPrincipal;
import com.hatodo.user.CurrentUserProvider;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Limit;
import org.springframework.test.util.ReflectionTestUtils;

/** Rows into lines (requirements/0019 R163, R167). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TrackerSeriesServiceTest {

    @Mock private TrackerReadingRepository readings;
    @Mock private TrackerService trackers;

    private final UUID meId = UUID.randomUUID();
    private AppUser me;
    private AppUser ben;
    private TrackerSeriesService service;

    private static AppUser user(String username, UUID id) {
        AppUser user = AppUser.member(username, username, "$2y$10$hash");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Tracker tracker(TrackerPeriod period, TrackerScope scope, Rollup rollup) {
        Tracker tracker = new Tracker(me, "Tracked", null, ListIcon.HEALTH, "kg", period, scope,
                rollup);
        ReflectionTestUtils.setField(tracker, "id", UUID.randomUUID());
        return tracker;
    }

    private TrackerReading reading(Tracker tracker, AppUser member, String day, String amount) {
        return new TrackerReading(tracker, member, LocalDate.parse(day), new BigDecimal(amount),
                null, me);
    }

    @BeforeEach
    void setUp() {
        me = user("ana", meId);
        ben = user("ben", UUID.randomUUID());
        CurrentUserProvider currentUser = () -> AppUserPrincipal.of(me);
        service = new TrackerSeriesService(readings, trackers,
                new HouseholdProperties("Asia/Singapore"), currentUser);
    }

    @Test
    void foldsSeveralReadingsInOneBucketByTheDeclaredRule() {
        Tracker spend = tracker(TrackerPeriod.MONTH, TrackerScope.SHARED, Rollup.SUM);
        when(trackers.requireReadable(spend.getId())).thenReturn(spend);
        when(readings.findInWindow(eq(spend.getId()), any(), any(), any(Limit.class)))
                .thenReturn(List.of(
                        reading(spend, null, "2026-11-01", "142"),
                        reading(spend, null, "2026-11-01", "318"),
                        reading(spend, null, "2026-11-01", "520")));

        TrackerSeriesResponse series = service.series(spend.getId(),
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 1), null);

        assertThat(series.series()).hasSize(1);
        assertThat(series.series().get(0).values()).hasSize(1);
        assertThat(series.series().get(0).values().get(0)).isEqualByComparingTo("980");
    }

    /**
     * A bucket nobody recorded is a gap, drawn as a break - never a zero. A
     * missed weigh-in is not 0 kg, and a chart that drew it as one would invent
     * a crash (R167).
     */
    @Test
    void leavesAnUnrecordedBucketNullRatherThanZero() {
        Tracker weight = tracker(TrackerPeriod.DAY, TrackerScope.PER_MEMBER, Rollup.LATEST);
        when(trackers.requireReadable(weight.getId())).thenReturn(weight);
        when(readings.findInWindow(eq(weight.getId()), any(), any(), any(Limit.class)))
                .thenReturn(List.of(
                        reading(weight, me, "2026-11-17", "72.9"),
                        reading(weight, me, "2026-11-19", "72.4")));

        TrackerSeriesResponse series = service.series(weight.getId(),
                LocalDate.of(2026, 11, 17), LocalDate.of(2026, 11, 19), null);

        assertThat(series.buckets()).containsExactly(
                LocalDate.of(2026, 11, 17), LocalDate.of(2026, 11, 18), LocalDate.of(2026, 11, 19));
        assertThat(series.series().get(0).values().get(1)).isNull();
    }

    @Test
    void overlaysOneLinePerMemberOnAPerMemberTracker() {
        Tracker weight = tracker(TrackerPeriod.DAY, TrackerScope.PER_MEMBER, Rollup.LATEST);
        when(trackers.requireReadable(weight.getId())).thenReturn(weight);
        when(readings.findInWindow(eq(weight.getId()), any(), any(), any(Limit.class)))
                .thenReturn(List.of(
                        reading(weight, me, "2026-11-19", "72.4"),
                        reading(weight, ben, "2026-11-19", "81.0")));

        TrackerSeriesResponse series = service.series(weight.getId(),
                LocalDate.of(2026, 11, 19), LocalDate.of(2026, 11, 19), null);

        assertThat(series.series()).hasSize(2);
        assertThat(series.series()).extracting(TrackerSeriesResponse.Series::displayName)
                .containsExactlyInAnyOrder("ana", "ben");
    }

    /**
     * A line per member <em>with readings</em>, not one per grantee: an empty
     * line for somebody who has never recorded is a legend entry that explains
     * nothing.
     */
    @Test
    void drawsNoLineForAMemberWhoHasRecordedNothing() {
        Tracker weight = tracker(TrackerPeriod.DAY, TrackerScope.PER_MEMBER, Rollup.LATEST);
        when(trackers.requireReadable(weight.getId())).thenReturn(weight);
        when(readings.findInWindow(eq(weight.getId()), any(), any(), any(Limit.class)))
                .thenReturn(List.of(reading(weight, me, "2026-11-19", "72.4")));

        TrackerSeriesResponse series = service.series(weight.getId(),
                LocalDate.of(2026, 11, 19), LocalDate.of(2026, 11, 19), null);

        assertThat(series.series()).hasSize(1);
    }

    @Test
    void foldsDailyReadingsIntoMonthsWhenDrawnCoarsely() {
        Tracker weight = tracker(TrackerPeriod.DAY, TrackerScope.PER_MEMBER, Rollup.LATEST);
        when(trackers.requireReadable(weight.getId())).thenReturn(weight);
        when(readings.findInWindow(eq(weight.getId()), any(), any(), any(Limit.class)))
                .thenReturn(List.of(
                        reading(weight, me, "2026-10-02", "74.0"),
                        reading(weight, me, "2026-10-29", "73.1"),
                        reading(weight, me, "2026-11-19", "72.4")));

        TrackerSeriesResponse series = service.series(weight.getId(),
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 30), TrackerPeriod.MONTH);

        assertThat(series.buckets())
                .containsExactly(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1));
        // LATEST within October is the 29th's reading, not the 2nd's - the same
        // rule the bucket fold uses, applied one level up.
        assertThat(series.series().get(0).values().get(0)).isEqualByComparingTo("73.1");
        assertThat(series.series().get(0).values().get(1)).isEqualByComparingTo("72.4");
    }

    @Test
    void refusesToDrawAMonthlyTrackerByDay() {
        Tracker spend = tracker(TrackerPeriod.MONTH, TrackerScope.SHARED, Rollup.SUM);
        when(trackers.requireReadable(spend.getId())).thenReturn(spend);

        assertThatThrownBy(() -> service.series(spend.getId(), null, null, TrackerPeriod.DAY))
                .isInstanceOf(UnsupportedGrainException.class);
    }

    @Test
    void namesTheHouseholdsSeriesRatherThanLeavingItBlank() {
        Tracker spend = tracker(TrackerPeriod.MONTH, TrackerScope.SHARED, Rollup.SUM);
        when(trackers.requireReadable(spend.getId())).thenReturn(spend);
        when(readings.findInWindow(eq(spend.getId()), any(), any(), any(Limit.class)))
                .thenReturn(List.of(reading(spend, null, "2026-11-01", "980")));

        TrackerSeriesResponse series = service.series(spend.getId(),
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 1), null);

        assertThat(series.series().get(0).memberId()).isNull();
        assertThat(series.series().get(0).displayName()).isEqualTo("Everyone");
    }
}
