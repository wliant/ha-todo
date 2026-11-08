package com.hatodo.data;

import java.util.List;

/**
 * The sidebar's collections, in the order it draws them (0012 R98, R100, R102).
 *
 * <p>Its own response from its own controller rather than a second component on
 * {@code SidebarResponse}: the two groups are independent and nothing is drawn
 * from both, so there is no pair of values that can disagree - which is what
 * the old one-request-for-the-sidebar argument was actually protecting. See
 * adr/0042, and adr/0029 for why a {@code DataList} read does not happen inside
 * {@code ListController}.
 */
public record DataListSidebarResponse(List<DataListSummaryResponse> collections) {
}
