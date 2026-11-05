package com.hatodo.calendar;

import java.util.List;

/**
 * What came back from an address, before anything is saved (R65).
 *
 * <p>The member is about to publish this calendar to the people they tick, and
 * nothing in the product can tell a curated family calendar from somebody's
 * whole working life - both are a URL that parses. So the events are shown
 * exactly as the household would read them, and a mispaste is caught at the one
 * moment the member is paying attention.
 */
public record CalendarPreviewResponse(CalendarProvider provider, int totalInWindow,
                                      List<CalendarEventResponse> next) {
}
