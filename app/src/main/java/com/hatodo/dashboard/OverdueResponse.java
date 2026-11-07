package com.hatodo.dashboard;

import java.util.List;

/**
 * The open work whose day has gone (0011 R88).
 *
 * <p>Its own record rather than a shared one with {@link UndatedResponse},
 * which it matches field for field: they are two answers to two questions, and
 * a shared record is where a field meaning something to only one of them would
 * land.
 *
 * <p>Deliberately carries **no boundary**. The client is not told where today
 * starts because it does not get to decide (adr/0040), and a component holding
 * the answer invites a reader to recompute the question.
 *
 * @param total how many are late, which may exceed what {@code items} holds -
 *              the page is capped, and the first read of this in any household
 *              is every item ever dated and never finished
 */
public record OverdueResponse(List<DashboardItemResponse> items, long total) {
}
