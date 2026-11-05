package com.hatodo.recurrence;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The job that makes recurring items appear (R42).
 *
 * <p>Untransacted on purpose: it loops and catches, while
 * {@link RecurrenceMaterialiser} owns one transaction per rule. One rule
 * failing must not roll back the work of the rules before it.
 *
 * <p>No lock, no leader election, no shedlock. This assumes exactly one running
 * instance of the backend, which both compose files make true, and the unique
 * index on {@code (rule, occurrence)} makes a violation of that assumption
 * noisy and harmless rather than silent and wrong. See adr/0025.
 */
@Component
public class RecurrenceRunner {

    private static final Logger log = LoggerFactory.getLogger(RecurrenceRunner.class);

    private final RecurrenceRuleRepository rules;
    private final RecurrenceMaterialiser materialiser;

    public RecurrenceRunner(RecurrenceRuleRepository rules,
                            RecurrenceMaterialiser materialiser) {
        this.rules = rules;
        this.materialiser = materialiser;
    }

    @Scheduled(cron = "${app.recurrence.cron}", zone = "${app.recurrence.zone:UTC}")
    void scheduled() {
        RecurrenceRunResponse result = runEverything();
        log.info("Recurrence run: {} rules, {} generated, {} already there, {} failed",
                result.rulesScanned(), result.generated(), result.skipped(), result.failed());
    }

    /** Every rule in the deployment. The admin's ad-hoc trigger, and the cron. */
    public RecurrenceRunResponse runEverything() {
        LocalDate today = materialiser.today();
        // Widened by the largest lead a rule may carry, so a rule that creates
        // its work a month early is still found.
        return run(idsOf(rules.findDue(today.minusDays(30), today.plusDays(30))));
    }

    /** One list's rules, which is what a member triggers from its own tab. */
    public RecurrenceRunResponse runForList(UUID listId) {
        return run(idsOf(rules.findActiveForList(listId)));
    }

    private RecurrenceRunResponse run(List<UUID> due) {
        int generated = 0;
        int skipped = 0;
        int failed = 0;

        for (UUID ruleId : due) {
            try {
                RecurrenceMaterialiser.Outcome outcome = materialiser.materialise(ruleId);
                generated += outcome.generated();
                skipped += outcome.skipped();
            } catch (RuntimeException ex) {
                // Recorded against the rule as well as logged, because a job
                // nobody watches must be able to explain itself on the screen.
                failed++;
                log.warn("Recurrence rule {} could not run: {}", ruleId, ex.getMessage());
                try {
                    materialiser.recordFailure(ruleId, describe(ex));
                } catch (RuntimeException alsoBroken) {
                    log.warn("Could not record the failure of rule {}", ruleId, alsoBroken);
                }
            }
        }
        return new RecurrenceRunResponse(due.size(), generated, skipped, failed);
    }

    /**
     * Only the ids cross into the per-rule transaction.
     *
     * <p>This loop is deliberately untransacted, so these entities are
     * detached - passing one on would throw the moment the materialiser touched
     * a lazy collection.
     */
    private static List<UUID> idsOf(List<RecurrenceRule> due) {
        return due.stream().map(RecurrenceRule::getId).toList();
    }

    private static String describe(RuntimeException ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return "Could not create this item.";
        }
        return message.length() > 200 ? message.substring(0, 200) : message;
    }
}
