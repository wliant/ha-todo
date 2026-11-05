package com.hatodo.item;

import com.hatodo.list.CostMode;
import com.hatodo.list.FieldKind;
import com.hatodo.list.ListAccessRepository;
import com.hatodo.list.ListField;
import com.hatodo.list.ListFieldChoice;
import com.hatodo.list.ListFieldRepository;
import com.hatodo.list.TodoList;
import com.hatodo.list.TodoListService;
import com.hatodo.user.AppUser;
import com.hatodo.user.AppUserRepository;
import com.hatodo.images.ImageService;
import com.hatodo.images.StoredImage;
import com.hatodo.images.StoredImageRepository;
import com.hatodo.richtext.HtmlSanitizer;
import com.hatodo.user.CurrentUserProvider;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Items, and everything a list's configuration makes true about them.
 *
 * <p>Anyone who can see a list is a peer on its items: they may add, edit,
 * complete and delete any of them (R30). Only the list's own grant is checked,
 * never ownership - ownership is about the list's shape, not its contents.
 */
@Service
public class TodoItemService {

    private final TodoItemRepository items;
    private final ListFieldRepository fields;
    private final ListAccessRepository access;
    private final AppUserRepository users;
    private final TodoListService lists;
    private final CurrentUserProvider currentUser;
    private final HtmlSanitizer sanitizer;
    private final StoredImageRepository storedImages;
    private final ImageService images;

    /** Matches exactly what the sanitizer is willing to keep in an {@code img}. */
    private static final Pattern DETAIL_IMAGE =
            Pattern.compile("/api/images/([0-9a-fA-F-]{36})");

    public TodoItemService(TodoItemRepository items, ListFieldRepository fields,
                           ListAccessRepository access, AppUserRepository users,
                           TodoListService lists, CurrentUserProvider currentUser,
                           HtmlSanitizer sanitizer, StoredImageRepository storedImages,
                           ImageService images) {
        this.items = items;
        this.fields = fields;
        this.access = access;
        this.users = users;
        this.lists = lists;
        this.currentUser = currentUser;
        this.sanitizer = sanitizer;
        this.storedImages = storedImages;
        this.images = images;
    }

    @Transactional(readOnly = true)
    public List<ItemResponse> list(UUID listId, String status) {
        lists.requireReadable(listId);
        return items.findForList(listId, status == null ? "all" : status).stream()
                .map(ItemResponse::of)
                .toList();
    }

    @Transactional(readOnly = true)
    public ItemResponse get(UUID itemId) {
        return ItemResponse.of(readable(itemId));
    }

    @Transactional
    public ItemResponse create(UUID listId, SaveItemRequest request) {
        TodoList list = lists.requireReadable(listId);
        AppUser me = requireCurrentUser();

        TodoItem item = new TodoItem(list, request.description().trim(), me,
                items.highestPosition(listId) + 1);
        apply(item, list, request, me);
        TodoItem saved = items.save(item);
        claimImages(saved, me.getId());

        lists.recordInteraction(list, me);
        return ItemResponse.of(saved);
    }

    @Transactional
    public ItemResponse update(UUID itemId, SaveItemRequest request) {
        TodoItem item = readable(itemId);
        requireFreshView(item, request.version());
        AppUser me = requireCurrentUser();

        item.setDescription(request.description().trim());
        apply(item, item.getList(), request, me);
        claimImages(item, me.getId());

        lists.recordInteraction(item.getList(), me);
        return ItemResponse.of(item);
    }

    @Transactional
    public void delete(UUID itemId, Long version) {
        TodoItem item = readable(itemId);
        requireFreshView(item, version);
        // Its pictures go too. The foreign key cascade would remove their rows
        // and leave the bytes, after which nothing knows the bytes are there.
        forgetPicturesOf(itemId);
        items.delete(item);
    }

    /** Called when an item - or the whole list it is on - is going away. */
    @Transactional
    public void forgetPicturesOf(UUID itemId) {
        storedImages.findForItem(itemId).forEach(images::forget);
    }

    @Transactional
    public void forgetPicturesOnList(UUID listId) {
        storedImages.findForList(listId).forEach(images::forget);
    }

    /**
     * Completing or reopening an item (R30).
     *
     * <p>The points are computed here, from the configuration as it stands at
     * this moment, and written onto the item. They go to whoever completed it -
     * never the assignee - which is the whole point of points (R23, adr/0012).
     *
     * <p>Reopening keeps what the completion recorded and takes only the points
     * back (R49, adr/0024), so the note stays in the database and stops being
     * returned.
     */
    @Transactional
    public ItemResponse setCompleted(UUID itemId, CompletionRequest request) {
        TodoItem item = readable(itemId);
        requireFreshView(item, request.version());
        AppUser me = requireCurrentUser();

        if (request.completed()) {
            item.completeBy(me, pointsFor(item), sanitizer.sanitize(request.noteHtml()));
            // The note's pictures need handing to the item too, or every member
            // but the uploader sees a broken image (adr/0023).
            claimImages(item, me.getId());
        } else {
            if (request.noteHtml() != null) {
                // Refused rather than dropped: the CHECK would make this a 500,
                // and a silently discarded field hides a client bug.
                throw new InvalidCompletionException("A note belongs on a completed item.");
            }
            item.reopen(me);
        }

        lists.recordInteraction(item.getList(), me);
        return ItemResponse.of(item);
    }

    @Transactional(readOnly = true)
    public List<ListPointsResponse> points(UUID listId) {
        lists.requireReadable(listId);
        return items.pointsByMember(listId).stream()
                .map(row -> new ListPointsResponse((UUID) row[0], (String) row[1],
                        ((Number) row[2]).longValue()))
                .toList();
    }

    /**
     * Called when a grant is revoked: an item cannot stay assigned to somebody
     * who can no longer see the list (R24).
     */
    @Transactional
    public void unassignEverythingFrom(UUID listId, UUID userId) {
        for (TodoItem item : items.findAssignedTo(listId, userId)) {
            // assignTo clears assigned_by with the assignee, so a later grant
            // cannot leave a claim behind pointing at nobody.
            item.assignTo(null, null);
        }
    }

    // --- the rules ----------------------------------------------------------

    /**
     * Writes the configured fields onto the item, and refuses anything the
     * list did not ask for or did ask for and did not get.
     */
    private void apply(TodoItem item, TodoList list, SaveItemRequest request, AppUser actor) {
        Map<FieldKind, ListField> singletons = new HashMap<>();
        Map<UUID, ListField> costs = new HashMap<>();
        for (ListField field : fields.findForList(list.getId())) {
            if (field.getKind() == FieldKind.COST) {
                costs.put(field.getId(), field);
            } else {
                singletons.put(field.getKind(), field);
            }
        }

        applyDetail(item, singletons.get(FieldKind.DETAIL), request);
        applyAssignee(item, list, singletons.get(FieldKind.ASSIGNEE), request, actor);
        applyDueAt(item, singletons.get(FieldKind.DUE_AT), request);
        applyCosts(item, costs, request);
    }

    private void applyDetail(TodoItem item, ListField field, SaveItemRequest request) {
        if (field == null) {
            // The list does not ask for it, so storing it would be answering a
            // question nobody posed - and it would reappear if the field were
            // ever switched back on.
            item.setDetailHtml(null);
            return;
        }
        // Sanitized here, on write, so what is stored is already safe and no
        // read path has to remember to filter it (adr/0018).
        String detail = sanitizer.sanitize(request.detailHtml());
        if (field.isRequired() && detail == null) {
            throw new MissingRequiredFieldException("This list needs some detail on every item.");
        }
        item.setDetailHtml(detail);
    }

    private void applyAssignee(TodoItem item, TodoList list, ListField field,
                               SaveItemRequest request, AppUser actor) {
        if (field == null) {
            item.assignTo(null, actor);
            return;
        }
        if (request.assigneeId() == null) {
            if (field.isRequired()) {
                throw new MissingRequiredFieldException("This list needs every item assigned.");
            }
            item.assignTo(null, actor);
            return;
        }

        // The picker offering only the right people is a convenience; this is
        // the control (R24).
        if (!access.exists(list.getId(), request.assigneeId())) {
            throw new AssigneeWithoutAccessException();
        }
        // Recorded with whoever chose, so "they claimed it themselves" survives
        // a page load and reads the same for everybody (R47).
        item.assignTo(users.findById(request.assigneeId())
                .orElseThrow(AssigneeWithoutAccessException::new), actor);
    }

    private void applyDueAt(TodoItem item, ListField field, SaveItemRequest request) {
        if (field == null) {
            item.setDueAt(null);
            return;
        }
        if (request.dueAt() == null && field.isRequired()) {
            throw new MissingRequiredFieldException("This list needs a due date on every item.");
        }
        item.setDueAt(request.dueAt());
    }

    private void applyCosts(TodoItem item, Map<UUID, ListField> costs, SaveItemRequest request) {
        Map<UUID, ItemCostValueRequest> answered = new HashMap<>();
        for (ItemCostValueRequest value : request.costValuesOrEmpty()) {
            if (value.isEmpty()) {
                continue;
            }
            if (!costs.containsKey(value.fieldId())) {
                // Either a cost from another list, or one this list dropped.
                throw new MissingRequiredFieldException("That cost is not on this list.");
            }
            answered.put(value.fieldId(), value);
        }

        // Matched on the field it answers and rewritten in place, exactly as a
        // list's choices are: a row's identity is (item, field), so clearing
        // the collection and adding a freshly built row for a field the item
        // already answered is two objects for one identifier, which Hibernate
        // refuses outright rather than treating as an update.
        Map<UUID, ItemCostValue> existing = new HashMap<>();
        for (ItemCostValue value : item.getCostValues()) {
            existing.put(value.getField().getId(), value);
        }

        List<ItemCostValue> replacement = new ArrayList<>();
        for (ListField field : costs.values()) {
            ItemCostValueRequest value = answered.get(field.getId());
            if (value == null) {
                if (field.isRequired()) {
                    throw new MissingRequiredFieldException(
                            "This list needs " + field.getLabel() + " on every item.");
                }
                continue;
            }
            ItemCostValue row = existing.get(field.getId());
            if (row == null) {
                row = ItemCostValue.on(item, field);
            }
            answer(row, field, value);
            replacement.add(row);
        }

        // orphanRemoval deletes the rows nobody put back; the ones that are
        // put back are the same instances, so nothing is recreated.
        item.getCostValues().clear();
        item.getCostValues().addAll(replacement);
    }

    /** Writes one answer onto a row, in whichever way its field is measured. */
    private void answer(ItemCostValue row, ListField field, ItemCostValueRequest value) {
        if (field.getCostMode() == CostMode.FIXED) {
            if (value.choiceId() == null) {
                throw new MissingRequiredFieldException(
                        "Pick one of the choices for " + field.getLabel() + ".");
            }
            ListFieldChoice choice = field.getChoices().stream()
                    .filter(candidate -> candidate.getId().equals(value.choiceId()))
                    .findFirst()
                    .orElseThrow(() -> new MissingRequiredFieldException(
                            "That choice is not on " + field.getLabel() + "."));
            row.choose(choice);
            return;
        }

        BigDecimal amount = value.amount();
        if (amount == null) {
            throw new MissingRequiredFieldException(
                    "Put a number against " + field.getLabel() + ".");
        }
        row.measure(amount);
    }

    /** What this item is worth right now, across every cost it answered. */
    private int pointsFor(TodoItem item) {
        return item.getCostValues().stream().mapToInt(ItemCostValue::points).sum();
    }

    /**
     * Hands the pictures this item's detail and completion note refer to over
     * to the item, so from now on its list decides who may read them.
     *
     * <p>Only pictures this member uploaded, and only ones nobody has claimed
     * yet: an id alone must not be enough to drag somebody else's picture onto
     * a list they cannot see. Anything left unclaimed is the orphan adr/0013
     * tolerates.
     */
    private void claimImages(TodoItem item, UUID uploaderId) {
        Set<UUID> referenced = new java.util.HashSet<>();
        // Both rich-text bodies, because stored_image keys a claim on the item
        // alone and does not know which field referred to the picture. Scanning
        // only the detail leaves the note's pictures unclaimed, and therefore
        // readable by nobody but whoever uploaded them (adr/0023).
        for (String html : new String[] {item.getDetailHtml(), item.getCompletionNoteHtml()}) {
            if (html == null) {
                continue;
            }
            Matcher matcher = DETAIL_IMAGE.matcher(html);
            while (matcher.find()) {
                referenced.add(UUID.fromString(matcher.group(1)));
            }
        }

        for (UUID imageId : referenced) {
            storedImages.findById(imageId)
                    .filter(image -> image.getItemId() == null
                            || item.getId().equals(image.getItemId()))
                    .filter(image -> image.getUploadedBy() != null
                            && uploaderId.equals(image.getUploadedBy().getId()))
                    .ifPresent(image -> image.claimFor(item.getId()));
        }
    }

    private TodoItem readable(UUID itemId) {
        TodoItem item = items.findById(itemId)
                .orElseThrow(() -> new ItemNotFoundException(itemId));
        try {
            lists.requireReadable(item.getListId());
        } catch (RuntimeException ex) {
            // An item on a list the caller cannot see must be as invisible as
            // the list is - same answer, for the same reason (R19).
            throw new ItemNotFoundException(itemId);
        }
        return item;
    }

    /**
     * Checked before anything is mutated, so a stale write is a deterministic
     * 409 rather than one that depends on when Hibernate happens to flush.
     */
    private void requireFreshView(TodoItem item, Long seenVersion) {
        if (seenVersion == null || item.getVersion() != seenVersion) {
            throw new StaleItemException(item.getId());
        }
    }

    private AppUser requireCurrentUser() {
        UUID id = currentUser.requireUserId();
        return users.findById(id).orElseThrow(
                () -> new IllegalStateException("Authenticated user " + id + " no longer exists"));
    }
}
