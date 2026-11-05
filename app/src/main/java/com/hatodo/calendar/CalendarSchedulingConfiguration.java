package com.hatodo.calendar;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * The second scheduled thing in this product, and it carries its own
 * {@code @EnableScheduling} for the reason recurrence's does: the feature's
 * wiring travels with the feature, so {@code app.calendar.enabled: false}
 * switches the poll off - and an integration test that sets it cannot have a
 * refresh fire mid-assertion.
 *
 * <p>Its own annotation rather than a shared one, because recurrence can be
 * disabled while this is enabled and scheduling still has to be on.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.calendar.enabled", havingValue = "true",
        matchIfMissing = true)
class CalendarSchedulingConfiguration {
    // Nothing but the annotations.
}
