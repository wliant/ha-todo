package com.hatodo.dashboard;

import java.util.List;

/**
 * @param total how many there are, which may exceed what {@code items} holds:
 *              the page is capped, and this panel is long by construction until
 *              a household starts dating things
 */
public record UndatedResponse(List<DashboardItemResponse> items, long total) {
}
