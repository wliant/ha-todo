package com.hatodo.recurrence;

/**
 * What a run did, in counts.
 *
 * <p>Counts and nothing else, deliberately. The admin's ad-hoc run creates
 * items on lists the admin holds no grant on, which sits against adr/0010, and
 * is defensible only because it reads nothing back: no description, no list
 * name, no rule id. The obvious next request - a per-rule breakdown - has to be
 * refused for exactly that reason.
 *
 * @param skipped an occurrence that already existed, which is the unique index
 *                doing its job rather than an error
 */
public record RecurrenceRunResponse(int rulesScanned, int generated, int skipped, int failed) {
}
