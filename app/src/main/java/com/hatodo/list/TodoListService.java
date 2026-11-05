package com.hatodo.list;

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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lists, and who can see them.
 *
 * <p>Every authorization decision in this feature lives here, not in the
 * controller and not in the filter chain: the question is "does this caller
 * hold a grant on this row", which needs a lookup inside the transaction that
 * owns the write. Two predicates, and no third:
 *
 * <ul>
 *   <li><b>readable</b> - owner or a {@code list_access} row. Everyone who can
 *       see a list is a peer on it.
 *   <li><b>managed</b> - owner. Deliberately <em>not</em>
 *       {@code owner || isAdmin()}: the admin administers accounts, not list
 *       contents, and widening it here is exactly what R19 exists to prevent.
 * </ul>
 *
 * <p>No access answers 404 rather than 403, because R19 makes the existence of
 * a list the thing being hidden. See adr/0010.
 */
@Service
public class TodoListService {

    /** The sidebar is a glance, not a browse. */
    private static final int RECENT_LIMIT = 6;

    /**
     * How long a repeat visit stops counting as a fresh interaction. Long
     * enough that opening a list twice in a sitting writes once; short enough
     * that "recent" still means recent.
     */
    private static final int TOUCH_WINDOW_MINUTES = 15;

    private final TodoListRepository lists;
    private final ListAccessRepository access;
    private final ListUserStateRepository states;
    private final ListFieldRepository fields;
    private final AppUserRepository users;
    private final CurrentUserProvider currentUser;
    private final EntityManager entityManager;
    private final ApplicationEventPublisher events;

    public TodoListService(TodoListRepository lists, ListAccessRepository access,
                           ListUserStateRepository states, ListFieldRepository fields,
                           AppUserRepository users, CurrentUserProvider currentUser,
                           EntityManager entityManager, ApplicationEventPublisher events) {
        this.lists = lists;
        this.access = access;
        this.states = states;
        this.fields = fields;
        this.users = users;
        this.currentUser = currentUser;
        this.entityManager = entityManager;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<ListSummaryResponse> search(String query, ListIcon icon, String scope,
                                            boolean pinnedOnly) {
        UUID me = currentUser.requireUserId();
        String trimmed = query == null || query.isBlank() ? null : query.trim();
        String resolvedScope = scope == null ? "all" : scope;
        return lists.findVisible(me, trimmed, icon, resolvedScope, pinnedOnly).stream()
                .map(list -> summarise(list, me))
                .toList();
    }

    @Transactional(readOnly = true)
    public SidebarResponse sidebar() {
        UUID me = currentUser.requireUserId();
        List<ListSummaryResponse> pinned = states.findPinned(me).stream()
                .map(list -> summarise(list, me))
                .toList();
        List<ListSummaryResponse> recent =
                states.findRecent(me, PageRequest.of(0, RECENT_LIMIT)).stream()
                        .map(list -> summarise(list, me))
                        .toList();
        return new SidebarResponse(pinned, recent);
    }

    @Transactional(readOnly = true)
    public ListDetailResponse get(UUID listId) {
        UUID me = currentUser.requireUserId();
        return detail(readable(listId, me), me);
    }

    @Transactional
    public ListDetailResponse create(CreateListRequest request) {
        AppUser me = requireCurrentUser();
        String name = request.name().trim();
        if (lists.existsWithName(me.getId(), name)) {
            throw new ListNameTakenException(name);
        }

        TodoList list = lists.save(
                new TodoList(me, name, trimToNull(request.description()), request.icon()));

        // The owner's own grant, written in the same transaction, so every
        // later membership question is one table.
        access.save(new ListAccess(list, me, me));
        grantTo(list, me, request.memberIdsOrEmpty());

        // Every list has a description field whether or not anyone asked for
        // one (R21), and a due date field for the same reason since R40 - the
        // dashboard can only read dates from lists that declare the field
        // (adr/0022). Written here rather than by the field service, so that
        // service can depend on this one without the two depending on each
        // other.
        fields.save(ListField.description(list, (short) 0));
        fields.save(ListField.dueAt(list, (short) 1));

        // Creating a list is the strongest possible interaction with it.
        stateFor(list, me).touch();
        return detail(list, me.getId());
    }

    @Transactional
    public ListDetailResponse update(UUID listId, UpdateListRequest request) {
        AppUser me = requireCurrentUser();
        TodoList list = managed(listId, me.getId());
        requireFreshView(list, request.version());

        String name = request.name().trim();
        if (lists.existsOtherWithName(me.getId(), name, listId)) {
            throw new ListNameTakenException(name);
        }

        list.setName(name);
        list.setDescription(trimToNull(request.description()));
        list.setIcon(request.icon());
        stateFor(list, me).touch();
        return detail(list, me.getId());
    }

    @Transactional
    public void delete(UUID listId) {
        UUID me = currentUser.requireUserId();
        TodoList list = managed(listId, me);
        // Announced before the rows go: the cascade takes the pictures' rows
        // with them, and after that nothing remembers the bytes exist.
        events.publishEvent(new ListDeletedEvent(listId));
        lists.delete(list);
    }

    @Transactional(readOnly = true)
    public List<ListMemberResponse> members(UUID listId) {
        UUID me = currentUser.requireUserId();
        return membersOf(readable(listId, me));
    }

    @Transactional
    public List<ListMemberResponse> replaceMembers(UUID listId, UpdateAccessRequest request) {
        AppUser me = requireCurrentUser();
        TodoList list = managed(listId, me.getId());
        requireFreshView(list, request.version());

        Set<UUID> wanted = new LinkedHashSet<>(request.memberIdsOrEmpty());
        // The owner cannot be removed from their own list: a list nobody can
        // reach is not a state worth being able to reach.
        wanted.remove(me.getId());

        for (ListAccess existing : access.findForList(listId)) {
            if (!existing.getUserId().equals(me.getId()) && !wanted.contains(existing.getUserId())) {
                UUID removed = existing.getUserId();
                access.delete(existing);
                // Their items cannot stay assigned to them (R24).
                events.publishEvent(new ListAccessRevokedEvent(listId, removed));
            }
        }
        grantTo(list, me, wanted);

        // The access rows are children of the list, so changing them has to
        // move the list's version - otherwise a second editor holding the old
        // version is never told. Hibernate will not dirty-check a list whose
        // own columns did not change, which is what this is for.
        entityManager.lock(list, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
        stateFor(list, me).touch();
        return membersOf(list);
    }

    @Transactional
    public void pin(UUID listId, boolean pinned) {
        AppUser me = requireCurrentUser();
        TodoList list = readable(listId, me.getId());
        ListUserState state = stateFor(list, me);
        if (pinned) {
            state.pin();
        } else {
            state.unpin();
        }
    }

    /**
     * Records that the caller opened this list.
     *
     * <p>A write, on an explicit POST, rather than a side effect of the GET:
     * a read that writes is a trap, and it would turn every refetch-on-focus
     * into a row update.
     */
    @Transactional
    public void touch(UUID listId) {
        UUID me = currentUser.requireUserId();
        readable(listId, me);
        states.touch(listId, me, TOUCH_WINDOW_MINUTES);
    }

    // --- the two predicates -------------------------------------------------

    /** For services that own a list's children - the same rule, one caller up. */
    @Transactional(readOnly = true)
    public TodoList requireReadable(UUID listId) {
        return readable(listId, currentUser.requireUserId());
    }

    /**
     * The same question as {@link #requireReadable}, answered without throwing.
     *
     * <p>Catching the exception from that one does not undo the rollback-only
     * mark it leaves on the surrounding transaction, so a caller that wants a
     * yes or no - rather than to fail - has to ask this way.
     */
    @Transactional(readOnly = true)
    public boolean isReadable(UUID listId) {
        return lists.findVisibleById(listId, currentUser.requireUserId()).isPresent();
    }

    /**
     * Ownership plus a view that is still current, which is what every write
     * to a list's children needs before it starts.
     */
    @Transactional(readOnly = true)
    public TodoList requireManagedAndFresh(UUID listId, Long seenVersion) {
        TodoList list = managed(listId, currentUser.requireUserId());
        requireFreshView(list, seenVersion);
        return list;
    }

    /**
     * Records that this member just did something with this list.
     *
     * <p>Called from the writes themselves - an item added, completed or
     * edited - because that is the strongest evidence of interaction there is,
     * and the transaction is already open. Reads use the throttled POST
     * instead; see {@link #touch}.
     */
    @Transactional
    public void recordInteraction(TodoList list, AppUser member) {
        stateFor(list, member).touch();
    }

    /**
     * Moves the list's version because one of its children changed. Hibernate
     * will not dirty-check a list whose own columns are untouched, so without
     * this a second editor holding the old version is never told.
     */
    public void markChanged(TodoList list) {
        entityManager.lock(list, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
    }

    private TodoList readable(UUID listId, UUID userId) {
        return lists.findVisibleById(listId, userId)
                .orElseThrow(() -> new ListNotFoundException(listId));
    }

    private TodoList managed(UUID listId, UUID userId) {
        TodoList list = readable(listId, userId);
        if (!list.isOwnedBy(userId)) {
            throw new ListNotManagedException();
        }
        return list;
    }

    /**
     * Checked before anything is mutated, so the conflict is deterministic
     * rather than depending on when Hibernate flushes.
     */
    private void requireFreshView(TodoList list, Long seenVersion) {
        if (seenVersion == null || list.getVersion() != seenVersion) {
            throw new StaleListException();
        }
    }

    // --- helpers ------------------------------------------------------------

    private void grantTo(TodoList list, AppUser grantedBy, Iterable<UUID> memberIds) {
        for (UUID memberId : memberIds) {
            if (memberId == null || memberId.equals(list.getOwnerId())) {
                continue;
            }
            if (access.exists(list.getId(), memberId)) {
                continue;
            }
            AppUser member = users.findById(memberId)
                    .filter(AppUser::isEnabled)
                    .orElseThrow(UnknownMemberException::new);
            access.save(new ListAccess(list, member, grantedBy));
        }
    }

    private ListUserState stateFor(TodoList list, AppUser user) {
        return states.find(list.getId(), user.getId())
                .orElseGet(() -> states.save(new ListUserState(list, user)));
    }

    private boolean isPinned(UUID listId, UUID userId) {
        return states.find(listId, userId).map(ListUserState::isPinned).orElse(false);
    }

    private List<ListMemberResponse> membersOf(TodoList list) {
        List<ListMemberResponse> members = new ArrayList<>();
        for (ListAccess grant : access.findForList(list.getId())) {
            members.add(ListMemberResponse.of(grant.getUser(), grant.getUserId().equals(list.getOwnerId())));
        }
        return members;
    }

    private ListSummaryResponse summarise(TodoList list, UUID me) {
        return new ListSummaryResponse(list.getId(), list.getName(), list.getDescription(),
                list.getIcon(), list.isOwnedBy(me), isPinned(list.getId(), me),
                access.findForList(list.getId()).size());
    }

    private ListDetailResponse detail(TodoList list, UUID me) {
        return new ListDetailResponse(list.getId(), list.getName(), list.getDescription(),
                list.getIcon(), list.isOwnedBy(me), isPinned(list.getId(), me),
                list.getVersion(), membersOf(list), list.getCreatedAt());
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
