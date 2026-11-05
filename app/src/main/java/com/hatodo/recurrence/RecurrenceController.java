package com.hatodo.recurrence;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Standing instructions over HTTP (R41, R42).
 *
 * <p>Rules hang off the list for the collection and off their own id for the
 * item, as items do. Running them is a POST to a {@code runs} sub-collection:
 * a run is a thing that happens, not a resource being replaced.
 */
@RestController
@RequestMapping("/api")
public class RecurrenceController {

    private final RecurrenceRuleService rules;
    private final RecurrenceRunner runner;

    public RecurrenceController(RecurrenceRuleService rules, RecurrenceRunner runner) {
        this.rules = rules;
        this.runner = runner;
    }

    @GetMapping("/lists/{listId}/recurrence")
    public List<RecurrenceRuleResponse> list(@PathVariable UUID listId) {
        return rules.list(listId);
    }

    @PostMapping("/lists/{listId}/recurrence")
    @ResponseStatus(HttpStatus.CREATED)
    public RecurrenceRuleResponse create(@PathVariable UUID listId,
                                         @Valid @RequestBody SaveRecurrenceRuleRequest request) {
        return rules.create(listId, request);
    }

    @PatchMapping("/recurrence/{ruleId}")
    public RecurrenceRuleResponse update(@PathVariable UUID ruleId,
                                         @Valid @RequestBody SaveRecurrenceRuleRequest request) {
        return rules.update(ruleId, request);
    }

    @DeleteMapping("/recurrence/{ruleId}")
    public ResponseEntity<Void> delete(@PathVariable UUID ruleId, @RequestParam Long version) {
        rules.delete(ruleId, version);
        return ResponseEntity.noContent().build();
    }

    /**
     * Running this list's rules now.
     *
     * <p>Needs only a grant, not ownership: a member who has just written a
     * rule wants to see it work, and what it produces is an item they could
     * have typed by hand.
     */
    @PostMapping("/lists/{listId}/recurrence/runs")
    public RecurrenceRunResponse run(@PathVariable UUID listId) {
        rules.list(listId);
        return runner.runForList(listId);
    }
}
