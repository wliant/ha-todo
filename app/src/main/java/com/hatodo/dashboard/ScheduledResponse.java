package com.hatodo.dashboard;

import java.time.Instant;
import java.util.List;

/** @param from inclusive, {@code to} exclusive - the window the client asked for */
public record ScheduledResponse(Instant from, Instant to, List<DashboardItemResponse> items) {
}
