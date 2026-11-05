package com.hatodo.calendar;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param enabled            switches the feature's scheduler off, which is why
 *                           {@code @EnableScheduling} sits behind it
 * @param cron               when the poll runs, read in the household's zone
 * @param windowDaysBack     how much of the past is kept. Must cover the widest
 *                           view My day can ask for: the month grid starts at
 *                           the Monday of the week containing the 1st, so on
 *                           the 31st of a month that began on a Sunday it
 *                           reaches 36 days back. Short, and a month view
 *                           quietly loses its first row.
 * @param windowDaysForward  how much of the future is kept
 * @param staleAfter         how long a feed may go without a successful
 *                           refresh before My day says so (R71)
 * @param allowPrivateHosts  local and test only. True in a deployment turns
 *                           "add a calendar" into a request-forgery primitive
 *                           against the LAN - see docs/adr/0035.
 */
@ConfigurationProperties(prefix = "app.calendar")
public record CalendarProperties(Boolean enabled, String cron, Integer windowDaysBack,
                                 Integer windowDaysForward, Duration staleAfter,
                                 Boolean allowPrivateHosts) {

    /*
     * Named apart from the components they default, because a record accessor
     * has to keep its component's type - the same reason the request records
     * carry pointsOrZero() and isRequired().
     */

    public int daysBack() {
        return windowDaysBack == null ? 45 : windowDaysBack;
    }

    public int daysForward() {
        return windowDaysForward == null ? 90 : windowDaysForward;
    }

    public Duration staleAfterOrSixHours() {
        return staleAfter == null ? Duration.ofHours(6) : staleAfter;
    }

    /** Closed unless something says otherwise, which is the only safe default. */
    public boolean privateHostsAllowed() {
        return Boolean.TRUE.equals(allowPrivateHosts);
    }
}
