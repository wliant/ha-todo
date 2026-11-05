package com.hatodo.recurrence;

import com.hatodo.household.HouseholdProperties;
import com.hatodo.item.ItemCostValueRequest;
import com.hatodo.item.SaveItemRequest;
import com.hatodo.item.TodoItemRepository;
import com.hatodo.item.TodoItemService;
import com.hatodo.list.ListAccessRepository;
import com.hatodo.user.AppUser;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns one rule into the items it owes (R42).
 *
 * <p>One transaction per rule, which is why this is separate from the runner:
 * a rule that fails - a required field it cannot answer, an assignee who lost
 * access - must not roll back what the rules before it produced.
 *
 * <p>Takes a {@link Clock} rather than calling {@code LocalDate.now()}, so
 * "twice in one day" and "after five days down" are ordinary unit tests.
 */
@Component
public class RecurrenceMaterialiser {

    private static final Logger log = LoggerFactory.getLogger(RecurrenceMaterialiser.class);

    private final RecurrenceRuleRepository rules;
    private final RecurrenceCalculator calculator;
    private final TodoItemService items;
    private final TodoItemRepository occurrences;
    private final ListAccessRepository access;
    private final RecurrenceProperties properties;
    private final HouseholdProperties household;
    private final Clock clock;

    public RecurrenceMaterialiser(RecurrenceRuleRepository rules,
                                  RecurrenceCalculator calculator, TodoItemService items,
                                  TodoItemRepository occurrences, ListAccessRepository access,
                                  RecurrenceProperties properties,
                                  HouseholdProperties household, Clock clock) {
        this.rules = rules;
        this.calculator = calculator;
        this.items = items;
        this.occurrences = occurrences;
        this.access = access;
        this.properties = properties;
        this.household = household;
        this.clock = clock;
    }

    /** What a single rule's run produced. */
    public record Outcome(int generated, int skipped, boolean failed) {

        static final Outcome NOTHING = new Outcome(0, 0, false);
    }

    public LocalDate today() {
        return LocalDate.now(clock.withZone(household.zone()));
    }

    /**
     * Generates whatever this rule owes, and records that it ran.
     *
     * <p>The window starts the day after the high-water mark, so a second run
     * on the same day computes an empty range - that is the deterministic
     * guard. The unique index on {@code (rule, occurrence)} is the other one,
     * for two runs genuinely racing.
     */
    @Transactional
    public Outcome materialise(UUID ruleId) {
        // Loaded here, not handed in. The runner is untransacted on purpose -
        // one rule's failure must not roll back another's work - so anything it
        // passed would be detached, and the first touch of a lazy collection
        // would throw. Ids cross that boundary; entities do not.
        RecurrenceRule rule = rules.findById(ruleId).orElse(null);
        if (rule == null) {
            return Outcome.NOTHING;
        }

        LocalDate today = today();
        LocalDate through = today.plusDays(rule.getLeadDays());
        LocalDate from = rule.getLastMaterialisedOn() == null
                // A new rule only ever looks forward. Catch-up is opt-in
                // because a chore that was due last Tuesday is noise on Friday.
                ? today.minusDays(properties.catchUpDaysOrZero())
                : rule.getLastMaterialisedOn().plusDays(1);
        if (from.isBefore(today.minusDays(properties.catchUpDaysOrZero()))) {
            from = today.minusDays(properties.catchUpDaysOrZero());
        }

        List<LocalDate> dates = calculator.occurrences(rule, from, through);
        if (dates.isEmpty()) {
            rule.recordRun(through, null);
            return Outcome.NOTHING;
        }

        AppUser assignee = assigneeIfStillGranted(rule);
        String problem = assignee == null && rule.getAssignee() != null
                ? "Nobody could be assigned: %s no longer has access."
                        .formatted(rule.getAssignee().getDisplayName())
                : null;

        int generated = 0;
        int skipped = 0;
        for (LocalDate date : dates) {
            // Asked, not caught: the insert is deferred to commit, so a
            // violation of ux_todo_item_occurrence would arrive long after this
            // loop and could not be attributed to one occurrence. The index is
            // still the backstop for two runs genuinely racing - and that case
            // fails this rule's transaction, which the runner records.
            if (occurrences.existsByRuleIdAndOccurrenceOn(rule.getId(), date)) {
                log.debug("Occurrence of rule {} on {} already there", rule.getId(), date);
                skipped++;
                continue;
            }
            items.createFromRule(rule.getList(), rule.getCreatedBy(),
                    requestFor(rule, assignee, date), rule.getId(), date);
            generated++;
        }

        rule.recordRun(through, problem);
        return new Outcome(generated, skipped, false);
    }

    /** Records a failure against the rule without losing why. */
    @Transactional
    public void recordFailure(UUID ruleId, String message) {
        rules.findById(ruleId).ifPresent(rule -> rule.recordRun(null, message));
    }

    /**
     * The assignee, if they can still see the list.
     *
     * <p>A rule outlives a grant. Generating an item assigned to somebody who
     * cannot see the list would be worse than generating it unassigned, and
     * throwing would stop the whole household's recurrence over one rule.
     */
    private AppUser assigneeIfStillGranted(RecurrenceRule rule) {
        AppUser assignee = rule.getAssignee();
        if (assignee == null) {
            return null;
        }
        return access.exists(rule.getListId(), assignee.getId()) ? assignee : null;
    }

    private SaveItemRequest requestFor(RecurrenceRule rule, AppUser assignee, LocalDate date) {
        List<ItemCostValueRequest> costs = new ArrayList<>();
        rule.getCostValues().forEach(value -> costs.add(new ItemCostValueRequest(
                value.getField().getId(),
                value.getChoice() == null ? null : value.getChoice().getId(),
                value.getAmount())));

        return new SaveItemRequest(null, rule.getDescription(), rule.getDetailHtml(),
                assignee == null ? null : assignee.getId(), dueAt(rule, date), costs);
    }

    /**
     * The occurrence's due date, in the household's zone.
     *
     * <p>A rule's time of day is a wall clock: "the bins go out at 7pm" is 7pm
     * where the household lives, whatever the server's own zone happens to be.
     * Without {@code app.timezone} this would be 7pm UTC in every
     * container image.
     */
    private Instant dueAt(RecurrenceRule rule, LocalDate date) {
        if (rule.getDueTime() == null) {
            return null;
        }
        return date.atTime(rule.getDueTime()).atZone(household.zone()).toInstant();
    }
}
