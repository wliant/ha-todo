package com.hatodo.list;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hatodo.user.AppUser;
import com.hatodo.user.AppUserPrincipal;
import com.hatodo.user.AppUserRepository;
import com.hatodo.user.CurrentUserProvider;
import jakarta.persistence.EntityManager;
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
 * The rules that are awkward to reach over HTTP. Everything about who may see
 * what is asserted against real SQL instead, in ListApiIntegrationTest - a
 * predicate that filters in Java rather than in the query passes here and leaks
 * every list there.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TodoListServiceTest {

    @Mock private TodoListRepository lists;
    @Mock private ListAccessRepository access;
    @Mock private ListUserStateRepository states;
    @Mock private AppUserRepository users;
    @Mock private EntityManager entityManager;

    private final UUID meId = UUID.randomUUID();
    private AppUser me;
    private TodoListService service;

    private static AppUser user(String username, UUID id, boolean enabled) {
        AppUser user = AppUser.member(username, username, "$2y$10$hash");
        ReflectionTestUtils.setField(user, "id", id);
        ReflectionTestUtils.setField(user, "enabled", enabled);
        return user;
    }

    @BeforeEach
    void setUp() {
        me = user("ana", meId, true);
        when(users.findById(meId)).thenReturn(Optional.of(me));
        when(lists.save(any())).thenAnswer(call -> call.getArgument(0));
        when(states.find(any(), any())).thenReturn(Optional.empty());
        when(states.save(any())).thenAnswer(call -> call.getArgument(0));
        when(access.findForList(any())).thenReturn(List.of());
        CurrentUserProvider currentUser = () -> AppUserPrincipal.of(me);
        service = new TodoListService(lists, access, states, users, currentUser, entityManager);
    }

    @Test
    void grantsTheOwnerAccessToTheirOwnListAtCreation() {
        // Every later membership question is then one table and one index,
        // rather than an owner check OR'd with a join.
        service.create(new CreateListRequest("Groceries", null, ListIcon.GROCERIES, List.of()));

        ArgumentCaptor<ListAccess> granted = ArgumentCaptor.forClass(ListAccess.class);
        verify(access).save(granted.capture());
        assertThat(granted.getValue().getUserId()).isEqualTo(meId);
    }

    @Test
    void refusesToGrantAccessToSomeoneWhoIsDisabled() {
        UUID disabledId = UUID.randomUUID();
        when(users.findById(disabledId)).thenReturn(Optional.of(user("gone", disabledId, false)));
        CreateListRequest request =
                new CreateListRequest("Shared", null, ListIcon.CHECKLIST, List.of(disabledId));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(UnknownMemberException.class);
    }

    @Test
    void refusesToGrantAccessToSomeoneWhoDoesNotExist() {
        UUID ghost = UUID.randomUUID();
        when(users.findById(ghost)).thenReturn(Optional.empty());
        CreateListRequest request =
                new CreateListRequest("Shared", null, ListIcon.CHECKLIST, List.of(ghost));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(UnknownMemberException.class);
    }

    @Test
    void doesNotGrantTheOwnerTwiceWhenTheyAreAlsoNamedAsAMember() {
        service.create(new CreateListRequest("Mine", null, ListIcon.HOME, List.of(meId)));

        // One grant saved, not two: the explicit mention of the owner is a
        // no-op rather than a duplicate-key failure.
        verify(access, times(1)).save(any());
    }

    @Test
    void refusesANameTheOwnerAlreadyUses() {
        when(lists.existsWithName(meId, "Groceries")).thenReturn(true);
        CreateListRequest request =
                new CreateListRequest("  Groceries  ", null, ListIcon.GROCERIES, List.of());

        // Trimmed before the check, so trailing spaces cannot sneak a duplicate
        // past the unique index and turn a 409 into a 500.
        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(ListNameTakenException.class);
    }

    @Test
    void storesABlankDescriptionAsAbsentRatherThanEmpty() {
        ListDetailResponse created =
                service.create(new CreateListRequest("Trimmed", "   ", ListIcon.CHECKLIST, List.of()));

        assertThat(created.description()).isNull();
    }

    @Test
    void trimsTheNameItStores() {
        ListDetailResponse created =
                service.create(new CreateListRequest("  Garden  ", null, ListIcon.GARDEN, List.of()));

        assertThat(created.name()).isEqualTo("Garden");
    }

    @Test
    void recordsCreatingAListAsHavingInteractedWithIt() {
        // Otherwise a list you just made is missing from your own sidebar until
        // you happen to open it.
        service.create(new CreateListRequest("Fresh", null, ListIcon.CHECKLIST, List.of()));

        ArgumentCaptor<ListUserState> state = ArgumentCaptor.forClass(ListUserState.class);
        verify(states).save(state.capture());
        assertThat(state.getValue().getLastInteractedAt()).isNotNull();
    }
}
