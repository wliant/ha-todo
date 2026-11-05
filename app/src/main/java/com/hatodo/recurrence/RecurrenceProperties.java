package com.hatodo.recurrence;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param enabled     switches the feature and its scheduler off together, which
 *                    is why {@code @EnableScheduling} sits behind it
 * @param cron        when the job runs, read in {@link #zone}
 * @param catchUpDays how far back a run will create what it missed. Zero by
 *                    default: a chore that was due last Tuesday is not worth
 *                    creating on Friday, it is noise somebody has to delete.
 * @param zone        the zone a rule's time of day means, and the zone the cron
 *                    expression is read in. Unset, every container image
 *                    defaults to UTC and "the bins go out at 7pm" is wrong by
 *                    the offset.
 */
@ConfigurationProperties(prefix = "app.recurrence")
public record RecurrenceProperties(Boolean enabled, String cron, Integer catchUpDays,
                                   String zone) {

    /*
     * Named apart from the components they default, because a record accessor
     * has to keep its component's type - the same reason the request records
     * carry pointsOrZero() and isRequired().
     */

    public int catchUpDaysOrZero() {
        return catchUpDays == null ? 0 : catchUpDays;
    }

    public String zoneOrUtc() {
        return zone == null || zone.isBlank() ? "UTC" : zone;
    }
}
