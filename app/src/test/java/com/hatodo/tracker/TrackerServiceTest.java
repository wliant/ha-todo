package com.hatodo.tracker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hatodo.household.HouseholdProperties;
import com.hatodo.household.InteractionWindow;
import com.hatodo.list.ListIcon;
import com.hatodo.user.AppUser;
import com.hatodo.user.AppUserPrincipal;
import com.hatodo.user.AppUserRepository;
import com.hatodo.user.CurrentUserProvider;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * The rules awkward to reach over HTTP. Everything about who may see what is
 * asserted against real SQL in {@code TrackerApiIntegrationTest} instead - a
 * predicate that filters in Java rather than in the query passes here and leaks
 * every tracker.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TrackerServiceTest {

    @Mock private TrackerRepository trackers;
    @Mock private TrackerAccessRepository access;
    @Mock private TrackerReadingRepository readings;
    @Mock private TrackerUserStateRepository states;
    @Mock private TrackerInteractionRepository interactions;
    @Mock private AppUserRepository users;
    @Mock private EntityManager entityManager;

    private final UUID meId = UUID.randomUUID();
    private AppUser me;
    private TrackerService service;

    private static AppUser user(String username, UUID id, boolean enabled) {
        AppUser user = AppUser.member(username, username, "$2y$10$hash");
        ReflectionTestUtils.setField(user, "id", id);
        ReflectionTestUtils.setField(user, "enabled", enabled);
        return user;
    }

    private Tracker tracker(AppUser owner, TrackerPeriod period, TrackerScope scope) {
        Tracker tracker = new Tracker(owner, "Body Weight", null, ListIcon.HEALTH, "kg",
                period, scope, Rollup.LATEST);
        ReflectionTestUtils.setField(tracker, "id", UUID.randomUUID());
        return tracker;
    }

    private static CreateTrackerRequest creation(String name) {
        return new CreateTrackerRequest(name, null, ListIcon.HEALTH, "kg", TrackerPeriod.DAY,
                TrackerScope.PER_MEMBER, Rollup.LATEST, null, null, null, List.of());
    }

    @BeforeEach
    void setUp() {
        me = user("ana", meId, true);
        when(users.findById(meId)).thenReturn(Optional.of(me));
        when(trackers.save(any())).thenAnswer(call -> call.getArgument(0));
        when(access.findForTracker(any())).thenReturn(List.of());
        when(readings.countForTracker(any())).thenReturn(0L);
        CurrentUserProvider currentUser = () -> AppUserPrincipal.of(me);
        service = new TrackerService(trackers, access, readings, states, interactions,
                new InteractionWindow(new HouseholdProperties("Asia/Singapore")),
                users, currentUser, entityManager);
    }

    @Test
    void grantsTheOwnerAccessToTheirOwnTrackerAtCreation() {
        service.create(creation("Body Weight"));

        ArgumentCaptor<TrackerAccess> granted = ArgumentCaptor.forClass(TrackerAccess.class);
        verify(access).save(granted.capture());
        assertThat(granted.getValue().getUserId()).isEqualTo(meId);
    }

    /**
     * The regression guard against adr/0051's rejected designs.
     *
     * <p>A tracker built on either of the other two creates inherits what they
     * write - a DESCRIPTION and a DUE_AT field row, or a searchable document -
     * and a tracker with a due date is one of the things requirements/0019
     * exists to rule out. This service takes no field repository at all, so the
     * constructor is the assertion; this names why.
     */
    @Test
    void createWritesNoFieldConfigurationBecauseATrackerDeclaresNone() {
        service.create(creation("Body Weight"));

        verify(access, times(1)).save(any());
        assertThat(java.util.Arrays.stream(TrackerService.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getType)
                .map(Class::getSimpleName))
                .doesNotContain("ListFieldRepository");
    }

    @Test
    void refusesANameTheOwnerAlreadyUses() {
        when(trackers.existsWithName(meId, "Body Weight")).thenReturn(true);

        // Trimmed before the check, so trailing spaces cannot sneak a duplicate
        // past the unique index and turn a 409 into a 500.
        assertThatThrownBy(() -> service.create(creation("  Body Weight  ")))
                .isInstanceOf(TrackerNameTakenException.class);
    }

    @Test
    void refusesToGrantAccessToSomeoneWhoIsDisabled() {
        UUID disabledId = UUID.randomUUID();
        when(users.findById(disabledId)).thenReturn(Optional.of(user("gone", disabledId, false)));
        CreateTrackerRequest request = new CreateTrackerRequest("Shared", null, ListIcon.PAYMENTS,
                "SGD", TrackerPeriod.MONTH, TrackerScope.SHARED, Rollup.SUM, null, null, null,
                List.of(disabledId));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(UnknownTrackerMemberException.class);
    }

    @Test
    void aTargetNeedsADirectionAndADirectionATarget() {
        CreateTrackerRequest halfATarget = new CreateTrackerRequest("Weight", null,
                ListIcon.HEALTH, "kg", TrackerPeriod.DAY, TrackerScope.PER_MEMBER, Rollup.LATEST,
                null, new BigDecimal("70"), null, List.of());

        assertThatThrownBy(() -> service.create(halfATarget))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- R161, which is the rule most likely to be "simplified" away --------

    @Test
    void refusesToChangeThePeriodOnceATrackerHoldsReadings() {
        Tracker existing = tracker(me, TrackerPeriod.DAY, TrackerScope.PER_MEMBER);
        when(trackers.findVisibleById(existing.getId(), meId)).thenReturn(Optional.of(existing));
        when(readings.existsForTracker(existing.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.update(existing.getId(), update(existing,
                TrackerPeriod.MONTH, TrackerScope.PER_MEMBER)))
                .isInstanceOf(TrackerInUseException.class);
    }

    @Test
    void refusesToChangeWhoATrackerIsForOnceItHoldsReadings() {
        Tracker existing = tracker(me, TrackerPeriod.DAY, TrackerScope.PER_MEMBER);
        when(trackers.findVisibleById(existing.getId(), meId)).thenReturn(Optional.of(existing));
        when(readings.existsForTracker(existing.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.update(existing.getId(), update(existing,
                TrackerPeriod.DAY, TrackerScope.SHARED)))
                .isInstanceOf(TrackerInUseException.class);
    }

    /**
     * The half of R161 that is easy to get wrong in the other direction: a
     * settings screen sends every field back, so refusing on the *presence* of
     * period and scope rather than on a *change* to them would make every
     * tracker that holds readings unrenameable.
     */
    @Test
    void allowsARenameOnATrackerThatHoldsReadings() {
        Tracker existing = tracker(me, TrackerPeriod.DAY, TrackerScope.PER_MEMBER);
        when(trackers.findVisibleById(existing.getId(), meId)).thenReturn(Optional.of(existing));
        when(readings.existsForTracker(existing.getId())).thenReturn(true);

        service.update(existing.getId(), new UpdateTrackerRequest(0L, "Morning weight", null,
                ListIcon.HEALTH, "kg", TrackerPeriod.DAY, TrackerScope.PER_MEMBER, Rollup.LATEST,
                null, null, null));

        assertThat(existing.getName()).isEqualTo("Morning weight");
    }

    @Test
    void allowsAReshapeWhileTheTrackerIsStillEmpty() {
        Tracker existing = tracker(me, TrackerPeriod.DAY, TrackerScope.PER_MEMBER);
        when(trackers.findVisibleById(existing.getId(), meId)).thenReturn(Optional.of(existing));
        when(readings.existsForTracker(existing.getId())).thenReturn(false);

        service.update(existing.getId(),
                update(existing, TrackerPeriod.MONTH, TrackerScope.SHARED));

        assertThat(existing.getPeriod()).isEqualTo(TrackerPeriod.MONTH);
        assertThat(existing.getScope()).isEqualTo(TrackerScope.SHARED);
    }

    @Test
    void refusesToChangeATrackerSomebodyElseOwns() {
        AppUser ben = user("ben", UUID.randomUUID(), true);
        Tracker theirs = tracker(ben, TrackerPeriod.DAY, TrackerScope.PER_MEMBER);
        when(trackers.findVisibleById(theirs.getId(), meId)).thenReturn(Optional.of(theirs));

        // Visible, so not a 404 - but managed is ownership alone, and
        // deliberately not `owner || isAdmin()`.
        assertThatThrownBy(() -> service.update(theirs.getId(),
                update(theirs, TrackerPeriod.DAY, TrackerScope.PER_MEMBER)))
                .isInstanceOf(TrackerNotManagedException.class);
    }

    @Test
    void answersNotFoundRatherThanForbiddenWithoutAGrant() {
        UUID hidden = UUID.randomUUID();
        when(trackers.findVisibleById(hidden, meId)).thenReturn(Optional.empty());

        // The existence of a tracker is the thing being hidden (adr/0010).
        assertThatThrownBy(() -> service.get(hidden))
                .isInstanceOf(TrackerNotFoundException.class);
    }

    /**
     * A pin is the caller's own note about something they can see, so it is
     * readable rather than managed - pinning a tracker shared with you changes
     * nothing for the member who shared it.
     */
    @Test
    void pinsATrackerSomebodyElseOwns() {
        AppUser ben = user("ben", UUID.randomUUID(), true);
        Tracker theirs = tracker(ben, TrackerPeriod.DAY, TrackerScope.PER_MEMBER);
        when(trackers.findVisibleById(theirs.getId(), meId)).thenReturn(Optional.of(theirs));
        when(states.find(theirs.getId(), meId)).thenReturn(Optional.empty());
        when(states.save(any())).thenAnswer(call -> call.getArgument(0));

        service.pin(theirs.getId(), true);

        ArgumentCaptor<TrackerUserState> saved = ArgumentCaptor.forClass(TrackerUserState.class);
        verify(states).save(saved.capture());
        assertThat(saved.getValue().isPinned()).isTrue();
    }

    private static UpdateTrackerRequest update(Tracker tracker, TrackerPeriod period,
                                               TrackerScope scope) {
        return new UpdateTrackerRequest(tracker.getVersion(), tracker.getName(), null,
                tracker.getIcon(), tracker.getUnit(), period, scope, tracker.getRollup(),
                null, null, null);
    }
}
