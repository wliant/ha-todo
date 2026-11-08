package com.hatodo.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hatodo.list.ListIcon;
import com.hatodo.user.AppUser;
import com.hatodo.household.HouseholdProperties;
import com.hatodo.household.InteractionWindow;
import com.hatodo.user.AppUserPrincipal;
import com.hatodo.user.AppUserRepository;
import com.hatodo.user.CurrentUserProvider;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
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
 * asserted against real SQL in DataListApiIntegrationTest instead - a predicate
 * that filters in Java rather than in the query passes here and leaks every
 * collection there.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DataListServiceTest {

    @Mock private DataListRepository lists;
    @Mock private DataListAccessRepository access;
    @Mock private DataEntryRepository entries;
    @Mock private DataListInteractionRepository interactions;
    @Mock private AppUserRepository users;
    @Mock private EntityManager entityManager;
    @Mock private org.springframework.context.ApplicationEventPublisher events;

    private final UUID meId = UUID.randomUUID();
    private AppUser me;
    private DataListService service;

    private static AppUser user(String username, UUID id, boolean enabled) {
        AppUser user = AppUser.member(username, username, "$2y$10$hash");
        ReflectionTestUtils.setField(user, "id", id);
        ReflectionTestUtils.setField(user, "enabled", enabled);
        return user;
    }

    private DataList collection(AppUser owner, String name) {
        DataList list = new DataList(owner, name, null, ListIcon.GROCERIES);
        ReflectionTestUtils.setField(list, "id", UUID.randomUUID());
        return list;
    }

    @BeforeEach
    void setUp() {
        me = user("ana", meId, true);
        when(users.findById(meId)).thenReturn(Optional.of(me));
        when(lists.save(any())).thenAnswer(call -> call.getArgument(0));
        when(access.findForList(any())).thenReturn(List.of());
        when(entries.countForList(any())).thenReturn(0L);
        CurrentUserProvider currentUser = () -> AppUserPrincipal.of(me);
        service = new DataListService(lists, access, entries, interactions,
                new InteractionWindow(new HouseholdProperties("Asia/Singapore")),
                users, currentUser, entityManager, events);
    }

    @Test
    void grantsTheOwnerAccessToTheirOwnCollectionAtCreation() {
        service.create(new CreateDataListRequest("Recipes", null, ListIcon.GROCERIES, List.of()));

        ArgumentCaptor<DataListAccess> granted = ArgumentCaptor.forClass(DataListAccess.class);
        verify(access).save(granted.capture());
        assertThat(granted.getValue().getUserId()).isEqualTo(meId);
    }

    /**
     * The regression guard against adr/0029's rejected design.
     *
     * <p>If a collection is ever built on {@code TodoListService.create}, it
     * inherits the DESCRIPTION and DUE_AT field rows that service writes for
     * every list - and a collection with a due date is the one thing
     * requirements/0006 R50 exists to rule out. This service takes no field
     * repository at all, so the constructor is the assertion; this names why.
     */
    @Test
    void createWritesNoFieldConfigurationBecauseACollectionDeclaresNone() {
        service.create(new CreateDataListRequest("Recipes", null, ListIcon.GROCERIES, List.of()));

        // One save: the owner's grant. Nothing else, and in particular no
        // due-date field.
        verify(access, times(1)).save(any());
        assertThat(java.util.Arrays.stream(DataListService.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getType)
                .map(Class::getSimpleName))
                .doesNotContain("ListFieldRepository");
    }

    @Test
    void refusesANameTheOwnerAlreadyUses() {
        when(lists.existsWithName(meId, "Recipes")).thenReturn(true);
        CreateDataListRequest request =
                new CreateDataListRequest("  Recipes  ", null, ListIcon.GROCERIES, List.of());

        // Trimmed before the check, so trailing spaces cannot sneak a duplicate
        // past the unique index and turn a 409 into a 500.
        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(DataListNameTakenException.class);
    }

    @Test
    void refusesToGrantAccessToSomeoneWhoIsDisabled() {
        UUID disabledId = UUID.randomUUID();
        when(users.findById(disabledId)).thenReturn(Optional.of(user("gone", disabledId, false)));
        CreateDataListRequest request = new CreateDataListRequest(
                "Shared", null, ListIcon.CHECKLIST, List.of(disabledId));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(UnknownCollectionMemberException.class);
    }

    @Test
    void doesNotGrantTheOwnerTwiceWhenTheyAreAlsoNamedAsAMember() {
        service.create(new CreateDataListRequest("Mine", null, ListIcon.HOME, List.of(meId)));

        verify(access, times(1)).save(any());
    }

    @Test
    void storesABlankDescriptionAsAbsentRatherThanEmpty() {
        DataListDetailResponse created = service.create(
                new CreateDataListRequest("Trimmed", "   ", ListIcon.CHECKLIST, List.of()));

        assertThat(created.description()).isNull();
    }

    @Test
    void aCollectionWithoutAGrantIsNotFound() {
        UUID unknown = UUID.randomUUID();
        when(lists.findVisibleById(unknown, meId)).thenReturn(Optional.empty());

        // Not forbidden: R50 reuses R19, which hides the collection's very
        // existence.
        assertThatThrownBy(() -> service.get(unknown))
                .isInstanceOf(DataListNotFoundException.class);
    }

    @Test
    void aGrantHolderCannotManageIt() {
        AppUser owner = user("ben", UUID.randomUUID(), true);
        DataList theirs = collection(owner, "Theirs");
        when(lists.findVisibleById(theirs.getId(), meId)).thenReturn(Optional.of(theirs));

        assertThatThrownBy(() -> service.requireManaged(theirs.getId()))
                .isInstanceOf(DataListNotManagedException.class);
    }

    @Test
    void refusesAStaleUpdateBeforeMutatingAnything() {
        DataList mine = collection(me, "Recipes");
        when(lists.findVisibleById(mine.getId(), meId)).thenReturn(Optional.of(mine));

        assertThatThrownBy(() -> service.update(mine.getId(),
                new UpdateDataListRequest(7L, "Renamed", null, ListIcon.HOME)))
                .isInstanceOf(StaleDataListException.class);
        // The name is untouched, which is what "before mutating anything"
        // means - and why the 409 does not depend on flush timing.
        assertThat(mine.getName()).isEqualTo("Recipes");
    }

    @Test
    void refusesAnUpdateWithNoVersionAtAll() {
        DataList mine = collection(me, "Recipes");
        when(lists.findVisibleById(mine.getId(), meId)).thenReturn(Optional.of(mine));

        assertThatThrownBy(() -> service.update(mine.getId(),
                new UpdateDataListRequest(null, "Renamed", null, ListIcon.HOME)))
                .isInstanceOf(StaleDataListException.class);
    }

    @Test
    void replaceMembersKeepsTheOwnerEvenWhenTheyAreNotNamed() {
        DataList mine = collection(me, "Recipes");
        when(lists.findVisibleById(mine.getId(), meId)).thenReturn(Optional.of(mine));
        DataListAccess ownersOwn = new DataListAccess(mine, me, me);
        when(access.findForList(mine.getId())).thenReturn(List.of(ownersOwn));

        service.replaceMembers(mine.getId(), new UpdateDataListAccessRequest(0L, List.of()));

        // A collection nobody can reach is not a state worth reaching.
        verify(access, never()).delete(ownersOwn);
    }

    @Test
    void replaceMembersMovesTheVersionSoASecondEditorIsTold() {
        DataList mine = collection(me, "Recipes");
        when(lists.findVisibleById(mine.getId(), meId)).thenReturn(Optional.of(mine));

        service.replaceMembers(mine.getId(), new UpdateDataListAccessRequest(0L, List.of()));

        // Hibernate will not dirty-check a row whose own columns are
        // untouched, so without this the second editor never finds out.
        verify(entityManager).lock(mine, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
    }

    @Test
    void deleteAnnouncesItselfBeforeTheRowsGo() {
        DataList mine = collection(me, "Recipes");
        when(lists.findVisibleById(mine.getId(), meId)).thenReturn(Optional.of(mine));

        service.delete(mine.getId());

        // Announced while the ids are still readable: the cascade takes the
        // picture rows, and after that nothing remembers the bytes exist.
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(events, lists);
        order.verify(events).publishEvent(new DataListDeletedEvent(mine.getId()));
        order.verify(lists).delete(mine);
    }

    @Test
    void anAnonymousCallerIsNotReadableRatherThanAnError() {
        // The image endpoint is permitAll, so this is reached without a
        // session - and must answer no rather than throw inside a transaction.
        DataListService anonymous = new DataListService(lists, access, entries, interactions,
                new InteractionWindow(new HouseholdProperties("Asia/Singapore")), users,
                () -> {
                    throw new IllegalStateException("nobody");
                }, entityManager, events);

        assertThat(anonymous.isReadable(UUID.randomUUID())).isFalse();
    }
}
