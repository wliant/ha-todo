package com.hatodo.household;

import java.time.LocalDate;
import org.springframework.stereotype.Component;

/**
 * How far back "how often do I use this" looks, and how close two visits have to
 * be to count once (0012 R100, adr/0041).
 *
 * <p>One component rather than a constant in each service, because the sidebar
 * orders todo lists and collections from two separate queries in two separate
 * packages and a window that differs between them is a sidebar whose two groups
 * measure different things - which nobody would ever notice.
 *
 * <p>Days are the **household's** (adr/0036). {@code LocalDate.now()} with no
 * zone is the container's, which is UTC, which puts a Singapore evening on the
 * wrong day.
 */
@Component
public class InteractionWindow {

    /**
     * A month. Long enough that a weekly habit is visible, short enough that a
     * habit abandoned a month ago has stopped holding its place in the sidebar -
     * which is the decay R100 asks for and a monotonic counter cannot give.
     */
    public static final int DAYS = 30;

    /**
     * How long a repeat visit stops counting as a fresh one. Long enough that
     * opening a list twice in a sitting counts once; short enough that two real
     * visits in an afternoon count twice.
     *
     * <p>Shared by the recency upsert and the frequency upsert, which are
     * deliberately **separate statements** - see
     * {@code ListInteractionRepository#record}. One constant, two independent
     * writes; a write that reads the other's row to decide is an ordering
     * dependency inside one method.
     */
    public static final int DEDUP_MINUTES = 15;

    private final HouseholdProperties household;

    public InteractionWindow(HouseholdProperties household) {
        this.household = household;
    }

    /** Today, as the household reckons it. */
    public LocalDate today() {
        return LocalDate.now(household.zone());
    }

    /** The first day the frequency sum counts, and the first day a bucket is kept. */
    public LocalDate since() {
        return today().minusDays(DAYS);
    }
}
