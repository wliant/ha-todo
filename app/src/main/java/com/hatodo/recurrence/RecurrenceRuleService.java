package com.hatodo.recurrence;

import com.hatodo.item.ItemCostValueRequest;
import com.hatodo.list.CostMode;
import com.hatodo.list.FieldKind;
import com.hatodo.list.ListAccessRepository;
import com.hatodo.list.ListField;
import com.hatodo.list.ListFieldChoice;
import com.hatodo.list.ListFieldRepository;
import com.hatodo.list.TodoList;
import com.hatodo.list.TodoListService;
import com.hatodo.images.ImageService;
import com.hatodo.images.StoredImageRepository;
import com.hatodo.item.AssigneeWithoutAccessException;
import com.hatodo.richtext.HtmlSanitizer;
import com.hatodo.user.AppUser;
import com.hatodo.user.AppUserRepository;
import com.hatodo.user.CurrentUserProvider;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The standing instructions on a list (R41).
 *
 * <p>Writing one needs ownership, on R20's reading: the shape of a list is the
 * owner's business, and a rule is as much the shape of the list as its fields
 * are. Reading one, and running one by hand, needs only a grant - a member who
 * has just seen a rule wants to watch it work, and the result is an item they
 * could have typed themselves.
 */
@Service
public class RecurrenceRuleService {

    private final RecurrenceRuleRepository rules;
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

    public RecurrenceRuleService(RecurrenceRuleRepository rules, ListFieldRepository fields,
                                 ListAccessRepository access, AppUserRepository users,
                                 TodoListService lists, CurrentUserProvider currentUser,
                                 HtmlSanitizer sanitizer, StoredImageRepository storedImages,
                                 ImageService images) {
        this.rules = rules;
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
    public List<RecurrenceRuleResponse> list(UUID listId) {
        lists.requireReadable(listId);
        return rules.findForList(listId).stream().map(RecurrenceRuleResponse::of).toList();
    }

    @Transactional
    public RecurrenceRuleResponse create(UUID listId, SaveRecurrenceRuleRequest request) {
        // Managed, not merely readable: a standing instruction is the shape of
        // the list rather than its contents (R20).
        TodoList list = lists.requireManaged(listId);
        AppUser me = requireCurrentUser();

        RecurrenceRule rule = new RecurrenceRule(list, request.description().trim(), me,
                (short) (rules.highestPosition(listId) + 1));
        apply(rule, list, request, me);
        RecurrenceRule saved = rules.save(rule);
        claimImages(saved, me.getId());
        return RecurrenceRuleResponse.of(saved);
    }

    @Transactional
    public RecurrenceRuleResponse update(UUID ruleId, SaveRecurrenceRuleRequest request) {
        RecurrenceRule rule = managed(ruleId);
        requireFreshView(rule, request.version());
        AppUser me = requireCurrentUser();

        rule.setDescription(request.description().trim());
        apply(rule, rule.getList(), request, me);
        claimImages(rule, me.getId());
        return RecurrenceRuleResponse.of(rule);
    }

    @Transactional
    public void delete(UUID ruleId, Long version) {
        RecurrenceRule rule = managed(ruleId);
        requireFreshView(rule, version);
        // Its pictures go too. The cascade would drop their rows and leave the
        // bytes, after which nothing knows the bytes are there.
        forgetPicturesOf(ruleId);
        rules.delete(rule);
        // The occurrences it already made stay: they are ordinary items, and
        // rule_id goes null by the foreign key action. Deleting them instead
        // would be a destructive surprise nobody asked for.
    }

    @Transactional
    public void forgetPicturesOf(UUID ruleId) {
        storedImages.findForRule(ruleId).forEach(images::forget);
    }


    /**
     * Called when a grant is revoked.
     *
     * <p>Without this, a rule keeps assigning to somebody who can no longer see
     * the list, and every run from then on either refuses or quietly produces
     * work for a member who cannot look at it (R24).
     */
    @Transactional
    public void unassignEverythingFrom(UUID listId, UUID userId) {
        for (RecurrenceRule rule : rules.findAssignedTo(listId, userId)) {
            rule.assignTo(null, null);
        }
    }

    /** Whether any rule has picked this cost choice - so deleting it is a 409. */
    @Transactional(readOnly = true)
    public boolean usesChoice(UUID choiceId) {
        return rules.usesChoice(choiceId);
    }

    // --- the rules ----------------------------------------------------------

    private void apply(RecurrenceRule rule, TodoList list, SaveRecurrenceRuleRequest request,
                       AppUser actor) {
        applySchedule(rule, request);

        Map<FieldKind, ListField> singletons = new HashMap<>();
        Map<UUID, ListField> costs = new HashMap<>();
        for (ListField field : fields.findForList(list.getId())) {
            if (field.getKind() == FieldKind.COST) {
                costs.put(field.getId(), field);
            } else {
                singletons.put(field.getKind(), field);
            }
        }

        // Sanitized here, on write, so the occurrences it produces need no
        // second pass and no read path has to remember (adr/0018).
        rule.setDetailHtml(singletons.containsKey(FieldKind.DETAIL)
                ? sanitizer.sanitize(request.detailHtml())
                : null);

        applyAssignee(rule, list, singletons.get(FieldKind.ASSIGNEE), request, actor);
        rule.setDueTime(singletons.containsKey(FieldKind.DUE_AT) ? request.dueTime() : null);
        applyCosts(rule, costs, request);
    }

    private void applySchedule(RecurrenceRule rule, SaveRecurrenceRuleRequest request) {
        Integer days = null;
        Integer dayOfMonth = null;

        switch (request.frequency()) {
            case WEEKLY -> {
                days = Weekdays.of(new HashSet<>(request.daysOrEmpty()));
                if (!Weekdays.isValid(days)) {
                    throw new InvalidRecurrenceException("Pick at least one day of the week.");
                }
            }
            case MONTHLY -> {
                if (request.dayOfMonth() == null) {
                    throw new InvalidRecurrenceException("Pick a day of the month.");
                }
                dayOfMonth = request.dayOfMonth();
            }
            case DAILY -> {
                // Nothing else to carry.
            }
        }

        rule.schedule(request.frequency(), request.intervalOrOne(), days, dayOfMonth);
        rule.setLeadDays(request.leadDaysOrZero());
        rule.setPaused(request.isPaused());
        // Starting today unless told otherwise, so a member who just wrote a
        // rule sees it work rather than wondering when it begins.
        rule.setStartsOn(request.startsOn() == null ? LocalDate.now() : request.startsOn());
        rule.setEndsOn(request.endsOn());
        if (rule.getEndsOn() != null && rule.getEndsOn().isBefore(rule.getStartsOn())) {
            throw new InvalidRecurrenceException("That would end before it starts.");
        }
    }

    private void applyAssignee(RecurrenceRule rule, TodoList list, ListField field,
                               SaveRecurrenceRuleRequest request, AppUser actor) {
        if (field == null || request.assigneeId() == null) {
            rule.assignTo(null, actor);
            return;
        }
        // The same control an item's assignee goes through (R24).
        if (!access.exists(list.getId(), request.assigneeId())) {
            throw new AssigneeWithoutAccessException();
        }
        rule.assignTo(users.findById(request.assigneeId())
                .orElseThrow(AssigneeWithoutAccessException::new), actor);
    }

    private void applyCosts(RecurrenceRule rule, Map<UUID, ListField> costs,
                            SaveRecurrenceRuleRequest request) {
        Map<UUID, ItemCostValueRequest> answered = new HashMap<>();
        for (ItemCostValueRequest value : request.costValuesOrEmpty()) {
            if (value.isEmpty()) {
                continue;
            }
            if (!costs.containsKey(value.fieldId())) {
                throw new InvalidRecurrenceException("That cost is not on this list.");
            }
            answered.put(value.fieldId(), value);
        }

        // Matched on the field each answers and rewritten in place, for the
        // reason an item's costs are: identity is (rule, field), so a freshly
        // built row for a field already answered is two objects for one
        // identifier, which Hibernate refuses outright.
        Map<UUID, RecurrenceCostValue> existing = new HashMap<>();
        for (RecurrenceCostValue value : rule.getCostValues()) {
            existing.put(value.getField().getId(), value);
        }

        List<RecurrenceCostValue> replacement = new ArrayList<>();
        for (ListField field : costs.values()) {
            ItemCostValueRequest value = answered.get(field.getId());
            if (value == null) {
                if (field.isRequired()) {
                    throw new InvalidRecurrenceException(
                            "This list needs " + field.getLabel() + " on every item.");
                }
                continue;
            }
            RecurrenceCostValue row = existing.get(field.getId());
            if (row == null) {
                row = RecurrenceCostValue.on(rule, field);
            }
            answer(row, field, value);
            replacement.add(row);
        }

        rule.getCostValues().clear();
        rule.getCostValues().addAll(replacement);
    }

    private void answer(RecurrenceCostValue row, ListField field, ItemCostValueRequest value) {
        if (field.getCostMode() == CostMode.FIXED) {
            if (value.choiceId() == null) {
                throw new InvalidRecurrenceException(
                        "Pick one of the choices for " + field.getLabel() + ".");
            }
            ListFieldChoice choice = field.getChoices().stream()
                    .filter(candidate -> candidate.getId().equals(value.choiceId()))
                    .findFirst()
                    .orElseThrow(() -> new InvalidRecurrenceException(
                            "That choice is not on " + field.getLabel() + "."));
            row.choose(choice);
            return;
        }

        BigDecimal amount = value.amount();
        if (amount == null) {
            throw new InvalidRecurrenceException(
                    "Put a number against " + field.getLabel() + ".");
        }
        row.measure(amount);
    }

    /**
     * Hands the pictures a rule's detail refers to over to the rule.
     *
     * <p>To the rule and not to the occurrences it produces: an occurrence
     * claiming them would mean deleting that one item takes the bytes away from
     * every future one (adr/0023).
     */
    private void claimImages(RecurrenceRule rule, UUID uploaderId) {
        String detail = rule.getDetailHtml();
        if (detail == null) {
            return;
        }
        Set<UUID> referenced = new HashSet<>();
        Matcher matcher = DETAIL_IMAGE.matcher(detail);
        while (matcher.find()) {
            referenced.add(UUID.fromString(matcher.group(1)));
        }
        for (UUID imageId : referenced) {
            storedImages.findById(imageId)
                    .filter(image -> image.getItemId() == null)
                    .filter(image -> image.getRuleId() == null
                            || rule.getId().equals(image.getRuleId()))
                    .filter(image -> image.getUploadedBy() != null
                            && uploaderId.equals(image.getUploadedBy().getId()))
                    .ifPresent(image -> image.claimForRule(rule.getId()));
        }
    }

    private RecurrenceRule managed(UUID ruleId) {
        RecurrenceRule rule = rules.findById(ruleId)
                .orElseThrow(() -> new RecurrenceRuleNotFoundException(ruleId));
        try {
            lists.requireManaged(rule.getListId());
        } catch (RuntimeException ex) {
            // A rule on a list the caller cannot see must be as invisible as
            // the list - and one they can see but do not own is a 403 from
            // requireManaged, which this deliberately does not swallow.
            if (!lists.isReadable(rule.getListId())) {
                throw new RecurrenceRuleNotFoundException(ruleId);
            }
            throw ex;
        }
        return rule;
    }

    private void requireFreshView(RecurrenceRule rule, Long seenVersion) {
        if (seenVersion == null || rule.getVersion() != seenVersion) {
            throw new StaleRecurrenceRuleException(rule.getId());
        }
    }

    private AppUser requireCurrentUser() {
        UUID id = currentUser.requireUserId();
        return users.findById(id).orElseThrow(
                () -> new IllegalStateException("Authenticated user " + id + " no longer exists"));
    }
}
