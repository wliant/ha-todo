package com.hatodo.dashboard;

import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The dashboard (R44, R45).
 *
 * <p>Two endpoints rather than three or one. The ranges differ only by their
 * bounds, so three would be three copies of one query each carrying its own
 * access guard - and the guard is the thing to have exactly one of. The undated
 * panel is separate because it sits beside all three views, and sharing their
 * key would refetch it on every tab change.
 *
 * <p>No list id anywhere: the caller's grants are the scope, and those come
 * from the session.
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboard;

    public DashboardController(DashboardService dashboard) {
        this.dashboard = dashboard;
    }

    @GetMapping("/scheduled")
    public ScheduledResponse scheduled(@RequestParam Instant from, @RequestParam Instant to,
                                       @RequestParam(required = false) String scope) {
        return dashboard.scheduled(from, to, scope);
    }

    @GetMapping("/undated")
    public UndatedResponse undated(@RequestParam(required = false) String scope) {
        return dashboard.undated(scope);
    }
}
