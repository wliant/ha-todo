package com.hatodo.admin;

import com.hatodo.recurrence.RecurrenceRunResponse;
import com.hatodo.recurrence.RecurrenceRunner;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Making the recurrence job run now (R42).
 *
 * <p>This is the answer to "it didn't appear" that does not need a shell on the
 * server. It creates items on lists the admin holds no grant on, which sits
 * against adr/0010's "the admin administers accounts, not list contents", and
 * is defensible <strong>only</strong> because it reads nothing back: the
 * response is counts, never a description, never a list name, never a rule id.
 *
 * <p>The obvious next request - a per-rule breakdown so somebody can see which
 * rule failed - has to be refused here and asked for on the list's own tab,
 * where the caller holds the grant. See adr/0025.
 */
@RestController
@RequestMapping("/api/admin/recurrence")
public class AdminRecurrenceController {

    private final RecurrenceRunner runner;

    public AdminRecurrenceController(RecurrenceRunner runner) {
        this.runner = runner;
    }

    @PostMapping("/runs")
    public RecurrenceRunResponse run() {
        return runner.runEverything();
    }
}
