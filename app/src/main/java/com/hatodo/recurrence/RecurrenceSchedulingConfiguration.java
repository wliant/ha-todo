package com.hatodo.recurrence;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * The first scheduled anything in this product.
 *
 * <p>{@code @EnableScheduling} lives here rather than on the application class
 * so the feature's wiring travels with the feature: switching recurrence off
 * switches its scheduler off with it, and an integration test that sets
 * {@code app.recurrence.enabled: false} cannot have a job fire mid-assertion.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.recurrence.enabled", havingValue = "true",
        matchIfMissing = true)
class RecurrenceSchedulingConfiguration {
    // Nothing but the annotations: this exists to turn the scheduler on, and
    // the clock the materialiser needs is wired whether or not it is.
}
