package com.hatodo.household;

import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The household's zone, which is the whole product's zone.
 *
 * <p>One household has one definition of Thursday. Every date-to-day decision
 * reads this - the recurrence job's cron and a rule's time of day, the days My
 * day is bucketed into, and the day a calendar event is placed on - because a
 * grid that merges events and items cannot have two of them. See
 * docs/adr/0036.
 *
 * <p>Deliberately not {@code app.recurrence.zone} any more, and not duplicated
 * per feature: two names for one zone is how they come to disagree, and the
 * failure is a chore generated at the right hour and shown on the wrong day.
 *
 * @param timezone an IANA zone id. Unset, every container image is UTC and the
 *                 household's evening lands in the wrong day.
 */
@ConfigurationProperties(prefix = "app")
public record HouseholdProperties(String timezone) {

    /** Where this household actually is. */
    public static final String DEFAULT = "Asia/Singapore";

    /*
     * Named apart from the component it defaults, because a record accessor has
     * to keep its component's type - the same reason the request records carry
     * pointsOrZero() and isRequired().
     */
    public ZoneId zone() {
        return ZoneId.of(timezone == null || timezone.isBlank() ? DEFAULT : timezone);
    }
}
