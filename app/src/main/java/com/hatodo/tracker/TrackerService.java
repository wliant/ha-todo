package com.hatodo.tracker;

import com.hatodo.household.InteractionWindow;
import com.hatodo.list.ListIcon;
import com.hatodo.user.AppUser;
import com.hatodo.user.AppUserRepository;
import com.hatodo.user.CurrentUserProvider;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Trackers, and who can see them (requirements/0019 R159-R161, R164, R170).
 *
 * <p>Every authorization decision for this feature lives here - not in the
 * controller, not in the filter chain, not in the UI. Two predicates, and no
 * third:
 *
 * <ul>
 *   <li><b>readable</b> - owner or a {@code tracker_access} row. Everyone who
 *       can see a tracker reads every series on it and may record into it
 *       (R164, R165).
 *   <li><b>managed</b> - owner. Deliberately <em>not</em>
 *       {@code owner || isAdmin()}: the admin administers accounts, not
 *       tracker contents, and gains nothing on a tracker they were not granted.
 * </ul>
 *
 * <p>Which series a member may <em>correct</em> is a third question, and it
 * deliberately does not live here: it is one predicate on
 * {@link TrackerReading#isWritableBy}, because the service and the response
 * both need the same answer (adr/0049's rule).
 *
 * <p>No grant answers 404 rather than 403, because the existence of a tracker
 * is the thing being hidden (adr/0010).
 *
 * <p>This is deliberately the third near-copy of this shape. The three features
 * answer the same question about different things, and sharing the
 * implementation is what would couple them - adr/0029, applied again by
 * adr/0051.
 */
@Service
public class TrackerService {

    private final TrackerRepository trackers;
    private final TrackerAccessRepository access;
    private final TrackerReadingRepository readings;
    private final TrackerUserStateRepository states;
    private final TrackerInteractionRepository interactions;
    private final InteractionWindow window;
    private final AppUserRepository users;
    private final CurrentUserProvider currentUser;
    private final EntityManager entityManager;

    public TrackerService(TrackerRepository trackers, TrackerAccessRepository access,
                          TrackerReadingRepository readings, TrackerUserStateRepository states,
                          TrackerInteractionRepository interactions, InteractionWindow window,
                          AppUserRepository users, CurrentUserProvider currentUser,
                          EntityManager entityManager) {
        this.trackers = trackers;
        this.access = access;
        this.readings = readings;
        this.states = states;
        this.interactions = interactions;
        this.window = window;
        this.users = users;
        this.currentUser = currentUser;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public List<TrackerSummaryResponse> search(String query, ListIcon icon, String scope) {
        UUID me = currentUser.requireUserId();
        String trimmed = query == null || query.isBlank() ? null : query.trim();
        String resolvedScope = scope == null ? "all" : scope;
        return trackers.findVisible(me, trimmed, icon, resolvedScope).stream()
                .map(tracker -> summarise(tracker, me))
                .toList();
    }

    /**
     * The sidebar's trackers: the pinned ones, then the group (R170).
     *
     * <p>Pinned trackers are uncapped, because the section holding them
     * scrolls; the group below excludes them and is capped at the member's
     * preference, so the two partition what they can see.
     */
    @Transactional(readOnly = true)
    public TrackerSidebarResponse sidebar() {
        AppUser me = requireCurrentUser();
        List<TrackerSummaryResponse> pinned = trackers.findPinnedForSidebar(me.getId()).stream()
                .map(tracker -> summarise(tracker, me.getId()))
                .toList();
        List<TrackerSummaryResponse> group =
                trackers.findForSidebar(me.getId(), window.since(), me.getSidebarListCount())
                        .stream()
                        .map(tracker -> summarise(tracker, me.getId()))
                        .toList();
        return new TrackerSidebarResponse(pinned, group);
    }

    /**
     * Pins or unpins a tracker for the caller.
     *
     * <p>Readable, not managed: a pin is the caller's own note about something
     * they can see. Pinning something shared with you changes nothing for the
     * member who shared it.
     */
    @Transactional
    public void pin(UUID trackerId, boolean pinned) {
        AppUser me = requireCurrentUser();
        Tracker tracker = readable(trackerId, me.getId());
        TrackerUserState state = stateFor(tracker, me);
        if (pinned) {
            state.pin();
        } else {
            state.unpin();
        }
    }

    /**
     * "I opened this tracker." A POST rather than a side effect of the GET, for
     * the reason the other two kinds' touch is one: a read stays a read, CSRF
     * applies, and a prefetch cannot reorder somebody's sidebar.
     */
    @Transactional
    public void touch(UUID trackerId) {
        UUID me = currentUser.requireUserId();
        readable(trackerId, me);
        interactions.record(trackerId, me, window.today(), InteractionWindow.DEDUP_MINUTES);
        // Self-pruning, scoped to the member: the buckets worth dropping belong
        // to the trackers they stopped opening.
        interactions.prune(me, window.since());
    }

    @Transactional(readOnly = true)
    public TrackerDetailResponse get(UUID trackerId) {
        UUID me = currentUser.requireUserId();
        return detail(readable(trackerId, me), me);
    }

    @Transactional
    public TrackerDetailResponse create(CreateTrackerRequest request) {
        AppUser me = requireCurrentUser();
        String name = request.name().trim();
        if (trackers.existsWithName(me.getId(), name)) {
            throw new TrackerNameTakenException(name);
        }

        Tracker tracker = new Tracker(me, name, trimToNull(request.description()), request.icon(),
                request.unit().trim(), request.period(), request.scope(), request.rollup());
        tracker.setDecimals(request.decimalsOrDefault());
        tracker.setTarget(request.target(), request.targetDirection());
        trackers.save(tracker);

        // The owner's own grant, written in the same transaction, so every
        // later membership question is one table.
        access.save(new TrackerAccess(tracker, me, me));
        grantTo(tracker, me, request.memberIdsOrEmpty());

        // Deliberately nothing else. A tracker declares no fields: no
        // description field, no due date, nothing the other two kinds' create
        // would have written. The absence of those lines is what adr/0051 is
        // about, and TrackerServiceTest asserts it.
        return detail(tracker, me.getId());
    }

    @Transactional
    public TrackerDetailResponse update(UUID trackerId, UpdateTrackerRequest request) {
        AppUser me = requireCurrentUser();
        Tracker tracker = managed(trackerId, me.getId());
        requireFreshView(tracker, request.version());

        String name = request.name().trim();
        if (trackers.existsOtherWithName(me.getId(), name, trackerId)) {
            throw new TrackerNameTakenException(name);
        }

        // R161. Asked before anything is written, and only when the member
        // actually asked for a change: a settings screen that sends the current
        // values back unchanged must not be refused for a tracker that holds
        // readings, which is every tracker anybody uses.
        boolean reshaping = request.period() != tracker.getPeriod()
                || request.scope() != tracker.getScope();
        if (reshaping) {
            if (readings.existsForTracker(trackerId)) {
                throw new TrackerInUseException(
                        "This tracker already holds readings, so its period and who it is for "
                                + "cannot change.");
            }
            tracker.setPeriod(request.period());
            tracker.setScope(request.scope());
        }

        tracker.setName(name);
        tracker.setDescription(trimToNull(request.description()));
        tracker.setIcon(request.icon());
        tracker.setUnit(request.unit().trim());
        tracker.setRollup(request.rollup());
        tracker.setDecimals(request.decimalsOrDefault());
        tracker.setTarget(request.target(), request.targetDirection());
        return detail(tracker, me.getId());
    }

    @Transactional
    public void delete(UUID trackerId) {
        UUID me = currentUser.requireUserId();
        Tracker tracker = managed(trackerId, me);
        // No event to publish, unlike a collection: a reading owns no bytes in
        // object storage, because a note is plain text (adr/0051). The cascade
        // is the whole of it.
        trackers.delete(tracker);
    }

    @Transactional(readOnly = true)
    public List<TrackerMemberResponse> members(UUID trackerId) {
        UUID me = currentUser.requireUserId();
        return membersOf(readable(trackerId, me));
    }

    @Transactional
    public List<TrackerMemberResponse> replaceMembers(UUID trackerId,
                                                      UpdateTrackerAccessRequest request) {
        AppUser me = requireCurrentUser();
        Tracker tracker = managed(trackerId, me.getId());
        requireFreshView(tracker, request.version());

        Set<UUID> wanted = new LinkedHashSet<>(request.memberIdsOrEmpty());
        // The owner cannot be removed from their own tracker: one nobody can
        // reach is not a state worth being able to reach.
        wanted.remove(me.getId());

        for (TrackerAccess existing : access.findForTracker(trackerId)) {
            if (!existing.getUserId().equals(me.getId())
                    && !wanted.contains(existing.getUserId())) {
                access.delete(existing);
                // Their readings stay. A revoked grant hides the tracker from
                // them; it does not retract numbers the household has already
                // charted, and deleting them would silently change a shape
                // everybody else is reading.
            }
        }
        grantTo(tracker, me, wanted);

        // The access rows are children of the tracker, so changing them has to
        // move its version - otherwise a second editor holding the old version
        // is never told. Hibernate will not dirty-check a row whose own columns
        // did not change, which is what this is for.
        markChanged(tracker);
        return membersOf(tracker);
    }

    // --- the two predicates -------------------------------------------------

    /** For the service that owns a tracker's readings - the same rule, one caller up. */
    @Transactional(readOnly = true)
    public Tracker requireReadable(UUID trackerId) {
        return readable(trackerId, currentUser.requireUserId());
    }

    /** Ownership alone. */
    @Transactional(readOnly = true)
    public Tracker requireManaged(UUID trackerId) {
        return managed(trackerId, currentUser.requireUserId());
    }

    /** Ownership plus a view that is still current. */
    @Transactional(readOnly = true)
    public Tracker requireManagedAndFresh(UUID trackerId, Long seenVersion) {
        Tracker tracker = managed(trackerId, currentUser.requireUserId());
        requireFreshView(tracker, seenVersion);
        return tracker;
    }

    /**
     * Moves the tracker's version because one of its children changed.
     * Hibernate will not dirty-check a row whose own columns are untouched, so
     * without this a second editor holding the old version is never told.
     */
    public void markChanged(Tracker tracker) {
        entityManager.lock(tracker, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
    }

    private Tracker readable(UUID trackerId, UUID userId) {
        return trackers.findVisibleById(trackerId, userId)
                .orElseThrow(() -> new TrackerNotFoundException(trackerId));
    }

    private Tracker managed(UUID trackerId, UUID userId) {
        Tracker tracker = readable(trackerId, userId);
        if (!tracker.isOwnedBy(userId)) {
            throw new TrackerNotManagedException();
        }
        return tracker;
    }

    /**
     * Checked before anything is mutated, so the conflict is deterministic
     * rather than depending on when Hibernate flushes.
     */
    private void requireFreshView(Tracker tracker, Long seenVersion) {
        if (seenVersion == null || tracker.getVersion() != seenVersion) {
            throw new StaleTrackerException();
        }
    }

    // --- helpers ------------------------------------------------------------

    private TrackerUserState stateFor(Tracker tracker, AppUser me) {
        return states.find(tracker.getId(), me.getId())
                .orElseGet(() -> states.save(new TrackerUserState(tracker, me)));
    }

    private boolean isPinned(UUID trackerId, UUID userId) {
        return states.find(trackerId, userId).map(TrackerUserState::isPinned).orElse(false);
    }

    private void grantTo(Tracker tracker, AppUser grantedBy, Iterable<UUID> memberIds) {
        for (UUID memberId : memberIds) {
            if (memberId == null || memberId.equals(tracker.getOwnerId())) {
                continue;
            }
            if (access.exists(tracker.getId(), memberId)) {
                continue;
            }
            AppUser member = users.findById(memberId)
                    .filter(AppUser::isEnabled)
                    .orElseThrow(UnknownTrackerMemberException::new);
            access.save(new TrackerAccess(tracker, member, grantedBy));
        }
    }

    private List<TrackerMemberResponse> membersOf(Tracker tracker) {
        List<TrackerMemberResponse> members = new ArrayList<>();
        for (TrackerAccess grant : access.findForTracker(tracker.getId())) {
            members.add(TrackerMemberResponse.of(grant.getUser(),
                    grant.getUserId().equals(tracker.getOwnerId())));
        }
        return members;
    }

    TrackerSummaryResponse summarise(Tracker tracker, UUID me) {
        return new TrackerSummaryResponse(tracker.getId(), tracker.getName(),
                tracker.getDescription(), tracker.getIcon(), tracker.getUnit(),
                tracker.getPeriod(), tracker.getScope(), tracker.getRollup(),
                tracker.getDecimals(), tracker.getTarget(), tracker.getTargetDirection(),
                tracker.isOwnedBy(me), isPinned(tracker.getId(), me),
                access.findForTracker(tracker.getId()).size(),
                readings.countForTracker(tracker.getId()));
    }

    private TrackerDetailResponse detail(Tracker tracker, UUID me) {
        return new TrackerDetailResponse(tracker.getId(), tracker.getName(),
                tracker.getDescription(), tracker.getIcon(), tracker.getUnit(),
                tracker.getPeriod(), tracker.getScope(), tracker.getRollup(),
                tracker.getDecimals(), tracker.getTarget(), tracker.getTargetDirection(),
                tracker.isOwnedBy(me), isPinned(tracker.getId(), me),
                !readings.existsForTracker(tracker.getId()),
                tracker.getVersion(), membersOf(tracker), tracker.getCreatedAt());
    }

    private AppUser requireCurrentUser() {
        UUID id = currentUser.requireUserId();
        return users.findById(id).orElseThrow(
                () -> new IllegalStateException("Authenticated user " + id + " no longer exists"));
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
