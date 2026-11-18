package com.hatodo.tracker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.hatodo.household.HouseholdProperties;
import com.hatodo.list.ListIcon;
import com.hatodo.user.AppUser;
import com.hatodo.user.AppUserPrincipal;
import com.hatodo.user.AppUserRepository;
import com.hatodo.user.CurrentUserProvider;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Which series a number lands in, and who may correct it afterwards
 * (requirements/0019 R162, R165).
 *
 * <p>The two invariants that cannot be constrained in SQL - a shared tracker's
 * readings carry a null member, and a period start is always canonical - are
 * asserted here on the way in and again, through the API against real
 * Postgres, in {@code TrackerReadingApiIntegrationTest}. Both fail silently if
 * broken, so both are checked twice deliberately.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TrackerReadingServiceTest {

    @Mock private TrackerReadingRepository readings;
    @Mock private TrackerService trackers;
    @Mock private TrackerRepository trackerRepository;
    @Mock private AppUserRepository users;

    private final UUID meId = UUID.randomUUID();
    private final UUID benId = UUID.randomUUID();
    private AppUser me;
    private AppUser ben;
    private TrackerReadingService service;

    private static AppUser user(String username, UUID id) {
        AppUser user = AppUser.member(username, username, "$2y$10$hash");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Tracker tracker(TrackerPeriod period, TrackerScope scope) {
        Tracker tracker = new Tracker(me, "Body Weight", null, ListIcon.HEALTH, "kg",
                period, scope, Rollup.LATEST);
        ReflectionTestUtils.setField(tracker, "id", UUID.randomUUID());
        return tracker;
    }

    private TrackerReading reading(Tracker tracker, AppUser member) {
        TrackerReading reading = new TrackerReading(tracker, member, LocalDate.of(2026, 11, 19),
                new BigDecimal("72.4"), null, member == null ? me : member);
        ReflectionTestUtils.setField(reading, "id", UUID.randomUUID());
        return reading;
    }

    @BeforeEach
    void setUp() {
        me = user("ana", meId);
        ben = user("ben", benId);
        when(users.findById(meId)).thenReturn(Optional.of(me));
        when(users.findById(benId)).thenReturn(Optional.of(ben));
        when(readings.save(any())).thenAnswer(call -> call.getArgument(0));
        CurrentUserProvider currentUser = () -> AppUserPrincipal.of(me);
        service = new TrackerReadingService(readings, trackers, trackerRepository, users,
                currentUser, new HouseholdProperties("Asia/Singapore"));
    }

    /**
     * The series is derived from the scope and the caller, which is why
     * SaveReadingRequest carries no member id: there is no parameter to tamper
     * with (R165).
     */
    @Test
    void recordsIntoTheCallersOwnSeriesOnAPerMemberTracker() {
        Tracker tracker = tracker(TrackerPeriod.DAY, TrackerScope.PER_MEMBER);
        when(trackers.requireReadable(tracker.getId())).thenReturn(tracker);

        TrackerReadingResponse saved = service.record(tracker.getId(), new SaveReadingRequest(
                null, LocalDate.of(2026, 11, 19), new BigDecimal("72.4"), null));

        assertThat(saved.memberId()).isEqualTo(meId);
    }

    /**
     * NULL *is* "the household's" (adr/0052). A stray member id here would be
     * invisible to every read rather than an error, which is why this is
     * asserted rather than assumed.
     */
    @Test
    void recordsIntoTheHouseholdsSeriesOnASharedTracker() {
        Tracker tracker = tracker(TrackerPeriod.MONTH, TrackerScope.SHARED);
        when(trackers.requireReadable(tracker.getId())).thenReturn(tracker);

        TrackerReadingResponse saved = service.record(tracker.getId(), new SaveReadingRequest(
                null, LocalDate.of(2026, 11, 11), new BigDecimal("318"), "weekly shop"));

        assertThat(saved.memberId()).isNull();
        // Authorship survives even though the series does not name anybody.
        assertThat(saved.recordedByDisplayName()).isEqualTo("ana");
    }

    @Test
    void canonicalisesTheBucketSoAMonthCannotBeOpenedTwice() {
        Tracker tracker = tracker(TrackerPeriod.MONTH, TrackerScope.SHARED);
        when(trackers.requireReadable(tracker.getId())).thenReturn(tracker);

        TrackerReadingResponse saved = service.record(tracker.getId(), new SaveReadingRequest(
                null, LocalDate.of(2026, 11, 18), new BigDecimal("142"), null));

        assertThat(saved.periodStart()).isEqualTo(LocalDate.of(2026, 11, 1));
    }

    @Test
    void canonicalisesTheBucketOnAnEditToo() {
        Tracker tracker = tracker(TrackerPeriod.MONTH, TrackerScope.SHARED);
        TrackerReading existing = reading(tracker, null);
        when(readings.findWithTracker(existing.getId())).thenReturn(Optional.of(existing));
        when(trackers.requireReadable(tracker.getId())).thenReturn(tracker);

        service.update(existing.getId(), new SaveReadingRequest(
                0L, LocalDate.of(2026, 10, 30), new BigDecimal("980"), null));

        assertThat(existing.getPeriodStart()).isEqualTo(LocalDate.of(2026, 10, 1));
    }

    // --- R165: who may correct what ----------------------------------------

    @Test
    void refusesToCorrectAnotherMembersNumberOnAPerMemberTracker() {
        Tracker tracker = tracker(TrackerPeriod.DAY, TrackerScope.PER_MEMBER);
        TrackerReading bens = reading(tracker, ben);
        when(readings.findWithTracker(bens.getId())).thenReturn(Optional.of(bens));
        when(trackers.requireReadable(tracker.getId())).thenReturn(tracker);

        // 403, not 404: the caller is looking at this number on the chart.
        assertThatThrownBy(() -> service.update(bens.getId(), new SaveReadingRequest(
                0L, LocalDate.of(2026, 11, 19), new BigDecimal("1"), null)))
                .isInstanceOf(ReadingNotWritableException.class);
    }

    @Test
    void letsAnyoneWithAccessCorrectASharedTrackersNumber() {
        Tracker tracker = tracker(TrackerPeriod.MONTH, TrackerScope.SHARED);
        TrackerReading typedByBen = new TrackerReading(tracker, null, LocalDate.of(2026, 11, 1),
                new BigDecimal("318"), null, ben);
        ReflectionTestUtils.setField(typedByBen, "id", UUID.randomUUID());
        when(readings.findWithTracker(typedByBen.getId())).thenReturn(Optional.of(typedByBen));
        when(trackers.requireReadable(tracker.getId())).thenReturn(tracker);

        service.update(typedByBen.getId(), new SaveReadingRequest(
                0L, LocalDate.of(2026, 11, 1), new BigDecimal("381"), null));

        // The series is the household's, so its numbers are too.
        assertThat(typedByBen.getAmount()).isEqualByComparingTo("381");
    }

    /**
     * A tracker the caller holds no grant on stays a 404 - the two refusals are
     * not interchangeable, and readability is asked first.
     */
    @Test
    void answersNotFoundForAReadingOnATrackerTheCallerCannotSee() {
        Tracker tracker = tracker(TrackerPeriod.DAY, TrackerScope.PER_MEMBER);
        TrackerReading bens = reading(tracker, ben);
        when(readings.findWithTracker(bens.getId())).thenReturn(Optional.of(bens));
        when(trackers.requireReadable(tracker.getId()))
                .thenThrow(new TrackerNotFoundException(tracker.getId()));

        assertThatThrownBy(() -> service.delete(bens.getId()))
                .isInstanceOf(TrackerNotFoundException.class);
    }

    @Test
    void refusesAnEditDrawnFromAnOlderVersion() {
        Tracker tracker = tracker(TrackerPeriod.DAY, TrackerScope.PER_MEMBER);
        TrackerReading mine = reading(tracker, me);
        ReflectionTestUtils.setField(mine, "version", 3L);
        when(readings.findWithTracker(mine.getId())).thenReturn(Optional.of(mine));
        when(trackers.requireReadable(tracker.getId())).thenReturn(tracker);

        assertThatThrownBy(() -> service.update(mine.getId(), new SaveReadingRequest(
                1L, LocalDate.of(2026, 11, 19), new BigDecimal("72"), null)))
                .isInstanceOf(StaleReadingException.class);
    }

    // --- R171: the agenda's nudge ------------------------------------------

    @Test
    void namesOnlyTheDailyTrackersWithNothingRecordedToday() {
        Tracker weight = tracker(TrackerPeriod.DAY, TrackerScope.PER_MEMBER);
        Tracker reading = tracker(TrackerPeriod.DAY, TrackerScope.PER_MEMBER);
        when(trackerRepository.findDailyVisible(meId)).thenReturn(List.of(weight, reading));
        when(readings.trackersWithReading(any(), any(), any()))
                .thenReturn(List.of(weight.getId()));

        List<PendingTrackerResponse> pending = service.pending();

        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).id()).isEqualTo(reading.getId());
        // It names the bucket, so the Add control cannot recompute "today" from
        // the device (adr/0036).
        assertThat(pending.get(0).periodStart())
                .isEqualTo(LocalDate.now(java.time.ZoneId.of("Asia/Singapore")));
    }

    @Test
    void asksNothingWhenThereAreNoDailyTrackers() {
        when(trackerRepository.findDailyVisible(meId)).thenReturn(List.of());

        assertThat(service.pending()).isEmpty();
    }
}
