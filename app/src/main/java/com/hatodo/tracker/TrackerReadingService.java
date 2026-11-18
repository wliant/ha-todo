package com.hatodo.tracker;

import com.hatodo.household.HouseholdProperties;
import com.hatodo.user.AppUser;
import com.hatodo.user.AppUserRepository;
import com.hatodo.user.CurrentUserProvider;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The numbers themselves (requirements/0019 R162, R163, R165, R166).
 *
 * <p>Two rules shape every method here.
 *
 * <p><b>Recording needs only a grant.</b> Writing a number is the ordinary act
 * a grant has always meant, exactly as adding an item to a list is - so this
 * asks {@code requireReadable} and never {@code requireManaged}.
 *
 * <p><b>The series is derived, never requested.</b> Which series a reading
 * lands in comes from the tracker's scope and the caller
 * ({@link Tracker#seriesFor}), so {@link SaveReadingRequest} carries no member
 * id and there is nothing to tamper with. That is also what keeps the
 * cross-table invariant true: a SHARED tracker's readings carry a null member
 * because nothing else can be written there.
 */
@Service
public class TrackerReadingService {

    /**
     * A safety bound on the readings table, not pagination.
     *
     * <p>The screen shows the newest; a tracker with more than this is a
     * tracker whose table should have been paged, and hitting it is the signal
     * to do that rather than a number to raise.
     */
    private static final int MAX_ROWS = 500;

    private final TrackerReadingRepository readings;
    private final TrackerService trackers;
    private final TrackerRepository trackerRepository;
    private final AppUserRepository users;
    private final CurrentUserProvider currentUser;
    private final HouseholdProperties household;

    public TrackerReadingService(TrackerReadingRepository readings, TrackerService trackers,
                                 TrackerRepository trackerRepository, AppUserRepository users,
                                 CurrentUserProvider currentUser,
                                 HouseholdProperties household) {
        this.readings = readings;
        this.trackers = trackers;
        this.trackerRepository = trackerRepository;
        this.users = users;
        this.currentUser = currentUser;
        this.household = household;
    }

    @Transactional(readOnly = true)
    public List<TrackerReadingResponse> list(UUID trackerId) {
        UUID me = currentUser.requireUserId();
        trackers.requireReadable(trackerId);
        return readings.findForTracker(trackerId, Limit.of(MAX_ROWS)).stream()
                .map(reading -> TrackerReadingResponse.of(reading, me))
                .toList();
    }

    @Transactional
    public TrackerReadingResponse record(UUID trackerId, SaveReadingRequest request) {
        AppUser me = requireCurrentUser();
        Tracker tracker = trackers.requireReadable(trackerId);

        // Canonicalised here and nowhere else. A monthly tracker sent the 18th
        // stores the 1st, so a client cannot open a second November by sending
        // a different day of it (adr/0052).
        LocalDate bucket = tracker.getPeriod().startOf(request.periodStart());
        AppUser member = tracker.seriesFor(me.getId())
                .map(this::user)
                .orElse(null);

        TrackerReading reading = readings.save(new TrackerReading(
                tracker, member, bucket, request.amount(), trimToNull(request.note()), me));
        return TrackerReadingResponse.of(reading, me.getId());
    }

    @Transactional
    public TrackerReadingResponse update(UUID readingId, SaveReadingRequest request) {
        UUID me = currentUser.requireUserId();
        TrackerReading reading = writable(readingId, me);
        requireFreshView(reading, request.version());

        reading.setPeriodStart(reading.getTracker().getPeriod().startOf(request.periodStart()));
        reading.setAmount(request.amount());
        reading.setNote(trimToNull(request.note()));
        // The series is deliberately not touched: an edit corrects a number,
        // and moving one between members would be a different act with
        // different permissions.
        return TrackerReadingResponse.of(reading, me);
    }

    @Transactional
    public void delete(UUID readingId) {
        UUID me = currentUser.requireUserId();
        readings.delete(writable(readingId, me));
    }

    /**
     * The daily trackers with nothing recorded today (R171).
     *
     * <p>"Today" is the household's, read here rather than taken from a
     * parameter: a bound a caller supplies is a bound the device's clock can
     * leak into, which is adr/0040's reasoning about the overdue strip.
     */
    @Transactional(readOnly = true)
    public List<PendingTrackerResponse> pending() {
        UUID me = currentUser.requireUserId();
        List<Tracker> daily = trackerRepository.findDailyVisible(me);
        if (daily.isEmpty()) {
            return List.of();
        }
        LocalDate today = LocalDate.now(household.zone());
        List<UUID> ids = daily.stream().map(Tracker::getId).toList();
        List<UUID> answered = readings.trackersWithReading(ids, today, me);
        return daily.stream()
                .filter(tracker -> !answered.contains(tracker.getId()))
                .map(tracker -> new PendingTrackerResponse(tracker.getId(), tracker.getName(),
                        tracker.getIcon(), tracker.getUnit(), tracker.getDecimals(), today))
                .toList();
    }

    /**
     * The reading as it actually is, read in its own transaction.
     *
     * <p>The one that threw is being rolled back, so a 409 body read inside it
     * would be discarded - the same reason {@code TodoItemService} reads a
     * conflicting item this way.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public TrackerReadingResponse readFresh(UUID readingId) {
        UUID me = currentUser.requireUserId();
        return readings.findWithTracker(readingId)
                .map(reading -> TrackerReadingResponse.of(reading, me))
                .orElse(null);
    }

    /**
     * Readable first, writable second - and they are not interchangeable.
     *
     * <p>A reading on a tracker the caller holds no grant on must stay a
     * <b>404</b>: the tracker's existence is what is hidden (R164). One they
     * can see but did not write, on a per-member tracker, is a <b>403</b>: they
     * are looking at it on the chart, and pretending it is not there would be a
     * worse answer than refusing.
     */
    private TrackerReading writable(UUID readingId, UUID userId) {
        TrackerReading reading = readings.findWithTracker(readingId)
                .orElseThrow(() -> new TrackerReadingNotFoundException(readingId));
        // Throws 404 when the grant is missing, which is the answer we want.
        trackers.requireReadable(reading.getTrackerId());
        if (!reading.isWritableBy(userId)) {
            throw new ReadingNotWritableException();
        }
        return reading;
    }

    private void requireFreshView(TrackerReading reading, Long seenVersion) {
        if (seenVersion == null || reading.getVersion() != seenVersion) {
            throw new StaleReadingException();
        }
    }

    private AppUser user(UUID id) {
        return users.findById(id).orElseThrow(
                () -> new IllegalStateException("Authenticated user " + id + " no longer exists"));
    }

    private AppUser requireCurrentUser() {
        return user(currentUser.requireUserId());
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
