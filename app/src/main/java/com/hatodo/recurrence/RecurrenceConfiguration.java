package com.hatodo.recurrence;

import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wiring the feature needs whether or not its scheduler is running.
 *
 * <p>Separate from {@link RecurrenceSchedulingConfiguration} on purpose: that
 * one is conditional, and a clock declared inside it would vanish when
 * recurrence is switched off - taking the materialiser's dependency with it and
 * failing the whole context, including every integration test that disables the
 * job precisely to keep it quiet.
 */
@Configuration
class RecurrenceConfiguration {

    /**
     * Injected rather than called, so the materialiser's date arithmetic is
     * testable without waiting for midnight.
     */
    @Bean
    @ConditionalOnMissingBean
    Clock clock() {
        return Clock.systemUTC();
    }
}
