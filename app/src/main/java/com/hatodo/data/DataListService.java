package com.hatodo.data;

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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Collections, and who can see them (requirements/0006 R50).
 *
 * <p>Every authorization decision for this feature lives here - not in the
 * controller, not in the filter chain, not in the UI. Two predicates, and no
 * third:
 *
 * <ul>
 *   <li><b>readable</b> - owner or a {@code data_list_access} row. Everyone who
 *       can see a collection is a peer on its entries (R60).
 *   <li><b>managed</b> - owner. Deliberately <em>not</em>
 *       {@code owner || isAdmin()}: the admin administers accounts, not
 *       collection contents, and gains nothing on a collection they were not
 *       granted.
 * </ul>
 *
 * <p>No grant answers 404 rather than 403, because the existence of a
 * collection is the thing being hidden (adr/0010).
 *
 * <p>This is deliberately a near-copy of {@code TodoListService}. The two
 * answer the same question about different things, and sharing the
 * implementation is what would couple them - which is the whole of adr/0029.
 */
@Service
public class DataListService {

    private final DataListRepository lists;
    private final DataListAccessRepository access;
    private final DataEntryRepository entries;
    private final DataListUserStateRepository states;
    private final DataListInteractionRepository interactions;
    private final InteractionWindow window;
    private final AppUserRepository users;
    private final CurrentUserProvider currentUser;
    private final EntityManager entityManager;
    private final ApplicationEventPublisher events;

    public DataListService(DataListRepository lists, DataListAccessRepository access,
                           DataEntryRepository entries, DataListUserStateRepository states,
                           DataListInteractionRepository interactions, InteractionWindow window,
                           AppUserRepository users,
                           CurrentUserProvider currentUser, EntityManager entityManager,
                           ApplicationEventPublisher events) {
        this.lists = lists;
        this.access = access;
        this.entries = entries;
        this.states = states;
        this.interactions = interactions;
        this.window = window;
        this.users = users;
        this.currentUser = currentUser;
        this.entityManager = entityManager;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<DataListSummaryResponse> search(String query, ListIcon icon, String scope) {
        UUID me = currentUser.requireUserId();
        String trimmed = query == null || query.isBlank() ? null : query.trim();
        String resolvedScope = scope == null ? "all" : scope;
        return lists.findVisible(me, trimmed, icon, resolvedScope).stream()
                .map(list -> summarise(list, me))
                .toList();
    }

    /**
     * The sidebar's collections: the pinned ones, then the group (0012 R98,
     * R100, R102, R104-R106).
     *
     * <p>Pinned collections are uncapped, because the section holding them
     * scrolls; the group below excludes them and is capped at the member's
     * preference, so the two partition what they can see. A collection could
     * not be pinned at all until adr/0043.
     */
    @Transactional(readOnly = true)
    public DataListSidebarResponse sidebar() {
        AppUser me = users.findById(currentUser.requireUserId()).orElseThrow(
                () -> new IllegalStateException("Authenticated user no longer exists"));
        List<DataListSummaryResponse> pinned = lists.findPinnedForSidebar(me.getId()).stream()
                .map(list -> summarise(list, me.getId()))
                .toList();
        List<DataListSummaryResponse> group =
                lists.findForSidebar(me.getId(), window.since(), me.getSidebarListCount()).stream()
                        .map(list -> summarise(list, me.getId()))
                        .toList();
        return new DataListSidebarResponse(pinned, group);
    }

    /**
     * Pins or unpins a collection for the caller (0012 R104).
     *
     * <p>Readable, not managed: a pin is the caller's own note about something
     * they can see, exactly as it is for a list. Pinning something shared with
     * you changes nothing for the member who shared it.
     */
    @Transactional
    public void pin(UUID listId, boolean pinned) {
        AppUser me = requireCurrentUser();
        DataList list = readable(listId, me.getId());
        DataListUserState state = stateFor(list, me);
        if (pinned) {
            state.pin();
        } else {
            state.unpin();
        }
    }

    private DataListUserState stateFor(DataList list, AppUser me) {
        return states.find(list.getId(), me.getId())
                .orElseGet(() -> states.save(new DataListUserState(list, me)));
    }

    private boolean isPinned(UUID listId, UUID userId) {
        return states.find(listId, userId).map(DataListUserState::isPinned).orElse(false);
    }

    /**
     * "I opened this collection." A POST rather than a side effect of the GET,
     * for the reason a list's touch is one: a read stays a read, CSRF applies,
     * and a prefetch cannot reorder somebody's sidebar.
     *
     * <p>Unlike a list there is no recency row to write - the bucket is the
     * whole record, and its own upsert carries the dedup window (adr/0041).
     */
    @Transactional
    public void touch(UUID listId) {
        UUID me = currentUser.requireUserId();
        readable(listId, me);
        interactions.record(listId, me, window.today(), InteractionWindow.DEDUP_MINUTES);
        // Self-pruning, scoped to the member: the buckets worth dropping belong
        // to the collections they stopped opening.
        interactions.prune(me, window.since());
    }

    @Transactional(readOnly = true)
    public DataListDetailResponse get(UUID listId) {
        UUID me = currentUser.requireUserId();
        return detail(readable(listId, me), me);
    }

    @Transactional
    public DataListDetailResponse create(CreateDataListRequest request) {
        AppUser me = requireCurrentUser();
        String name = request.name().trim();
        if (lists.existsWithName(me.getId(), name)) {
            throw new DataListNameTakenException(name);
        }

        DataList list = lists.save(
                new DataList(me, name, trimToNull(request.description()), request.icon()));

        // The owner's own grant, written in the same transaction, so every
        // later membership question is one table.
        access.save(new DataListAccess(list, me, me));
        grantTo(list, me, request.memberIdsOrEmpty());

        // Deliberately nothing else. A collection declares no fields: it has no
        // description field to configure and, above all, no due date (R50). The
        // absence of these two lines is what adr/0029 is about, and
        // DataListServiceTest asserts it.
        return detail(list, me.getId());
    }

    @Transactional
    public DataListDetailResponse update(UUID listId, UpdateDataListRequest request) {
        AppUser me = requireCurrentUser();
        DataList list = managed(listId, me.getId());
        requireFreshView(list, request.version());

        String name = request.name().trim();
        if (lists.existsOtherWithName(me.getId(), name, listId)) {
            throw new DataListNameTakenException(name);
        }

        list.setName(name);
        list.setDescription(trimToNull(request.description()));
        list.setIcon(request.icon());
        return detail(list, me.getId());
    }

    @Transactional
    public void delete(UUID listId) {
        UUID me = currentUser.requireUserId();
        DataList list = managed(listId, me);
        // Announced before the rows go: the cascade takes the entries and their
        // picture rows with them, and after that nothing remembers the bytes.
        events.publishEvent(new DataListDeletedEvent(listId));
        lists.delete(list);
    }

    @Transactional(readOnly = true)
    public List<DataListMemberResponse> members(UUID listId) {
        UUID me = currentUser.requireUserId();
        return membersOf(readable(listId, me));
    }

    @Transactional
    public List<DataListMemberResponse> replaceMembers(UUID listId,
                                                       UpdateDataListAccessRequest request) {
        AppUser me = requireCurrentUser();
        DataList list = managed(listId, me.getId());
        requireFreshView(list, request.version());

        Set<UUID> wanted = new LinkedHashSet<>(request.memberIdsOrEmpty());
        // The owner cannot be removed from their own collection: one nobody can
        // reach is not a state worth being able to reach.
        wanted.remove(me.getId());

        for (DataListAccess existing : access.findForList(listId)) {
            if (!existing.getUserId().equals(me.getId())
                    && !wanted.contains(existing.getUserId())) {
                access.delete(existing);
                // No revocation event, unlike a list: entries are never
                // assigned, so losing a grant has no consequence on them beyond
                // the collection becoming invisible.
            }
        }
        grantTo(list, me, wanted);

        // The access rows are children of the collection, so changing them has
        // to move its version - otherwise a second editor holding the old
        // version is never told. Hibernate will not dirty-check a row whose own
        // columns did not change, which is what this is for.
        markChanged(list);
        return membersOf(list);
    }

    // --- the two predicates -------------------------------------------------

    /** For the service that owns a collection's entries - the same rule, one caller up. */
    @Transactional(readOnly = true)
    public DataList requireReadable(UUID listId) {
        return readable(listId, currentUser.requireUserId());
    }

    /**
     * The same question as {@link #requireReadable}, answered without throwing.
     *
     * <p>Catching the exception from that one does not undo the rollback-only
     * mark it leaves on the surrounding transaction, and the commit then fails
     * with a 500 where a 404 was the point. The image access check has to ask
     * this way.
     */
    @Transactional(readOnly = true)
    public boolean isReadable(UUID listId) {
        return currentUser.find().isPresent()
                && lists.findVisibleById(listId, currentUser.requireUserId()).isPresent();
    }

    /** Ownership alone. */
    @Transactional(readOnly = true)
    public DataList requireManaged(UUID listId) {
        return managed(listId, currentUser.requireUserId());
    }

    /** Ownership plus a view that is still current. */
    @Transactional(readOnly = true)
    public DataList requireManagedAndFresh(UUID listId, Long seenVersion) {
        DataList list = managed(listId, currentUser.requireUserId());
        requireFreshView(list, seenVersion);
        return list;
    }

    /**
     * Moves the collection's version because one of its children changed.
     * Hibernate will not dirty-check a row whose own columns are untouched, so
     * without this a second editor holding the old version is never told.
     */
    public void markChanged(DataList list) {
        entityManager.lock(list, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
    }

    private DataList readable(UUID listId, UUID userId) {
        return lists.findVisibleById(listId, userId)
                .orElseThrow(() -> new DataListNotFoundException(listId));
    }

    private DataList managed(UUID listId, UUID userId) {
        DataList list = readable(listId, userId);
        if (!list.isOwnedBy(userId)) {
            throw new DataListNotManagedException();
        }
        return list;
    }

    /**
     * Checked before anything is mutated, so the conflict is deterministic
     * rather than depending on when Hibernate flushes.
     */
    private void requireFreshView(DataList list, Long seenVersion) {
        if (seenVersion == null || list.getVersion() != seenVersion) {
            throw new StaleDataListException();
        }
    }

    // --- helpers ------------------------------------------------------------

    private void grantTo(DataList list, AppUser grantedBy, Iterable<UUID> memberIds) {
        for (UUID memberId : memberIds) {
            if (memberId == null || memberId.equals(list.getOwnerId())) {
                continue;
            }
            if (access.exists(list.getId(), memberId)) {
                continue;
            }
            AppUser member = users.findById(memberId)
                    .filter(AppUser::isEnabled)
                    .orElseThrow(UnknownCollectionMemberException::new);
            access.save(new DataListAccess(list, member, grantedBy));
        }
    }

    private List<DataListMemberResponse> membersOf(DataList list) {
        List<DataListMemberResponse> members = new ArrayList<>();
        for (DataListAccess grant : access.findForList(list.getId())) {
            members.add(DataListMemberResponse.of(grant.getUser(),
                    grant.getUserId().equals(list.getOwnerId())));
        }
        return members;
    }

    private DataListSummaryResponse summarise(DataList list, UUID me) {
        return new DataListSummaryResponse(list.getId(), list.getName(), list.getDescription(),
                list.getIcon(), list.isOwnedBy(me), isPinned(list.getId(), me),
                access.findForList(list.getId()).size(),
                entries.countForList(list.getId()));
    }

    private DataListDetailResponse detail(DataList list, UUID me) {
        return new DataListDetailResponse(list.getId(), list.getName(), list.getDescription(),
                list.getIcon(), list.isOwnedBy(me), isPinned(list.getId(), me), list.getVersion(),
                membersOf(list), list.getCreatedAt());
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
