package com.hatodo.dashboard;

import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The dashboard (R44, R45).
 *
 * <p>Three endpoints rather than five or one. The ranges differ only by their
 * bounds, so three would be three copies of one query each carrying its own
 * access guard - and the guard is the thing to have exactly one of. The undated
 * panel is separate because it sits beside all three views, and sharing their
 * key would refetch it on every tab change. Overdue work is separate for the
 * same reason and a stronger one: it is not a period at all, it is reckoned
 * from the clock rather than from anything the client sends, and folding it
 * into the ranged read would give one query's rows two meanings (adr/0040).
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

    /**
     * What is late (0011 R88). Takes no window - on purpose, and the whole
     * point: see adr/0040.
     */
    @GetMapping("/overdue")
    public OverdueResponse overdue(@RequestParam(required = false) String scope) {
        return dashboard.overdue(scope);
    }
}
