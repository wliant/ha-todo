package com.hatodo.calendar;

import java.time.LocalDate;

/**
 * The days events are kept for (requirements/0008 R77).
 *
 * <p>Reported rather than left for the client to assume, because it is
 * configuration ({@code CALENDAR_WINDOW_BACK} / {@code _FORWARD}) and a client
 * holding its own copy of the numbers would be wrong in exactly the deployment
 * that changed them.
 *
 * <p>Household dates, because a day is where an event is placed and an instant
 * is not.
 *
 * <p>There is deliberately no equivalent for items: they are returned for
 * whatever window is asked for. That asymmetry is real, and it is why a member
 * stepping into next February sees their chores and none of the appointments -
 * which the notice has to say rather than leave them to infer.
 */
public record EventWindowResponse(LocalDate from, LocalDate to) {
}
