package com.hatodo.calendar;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Refreshes every feed on a schedule.
 *
 * <p>Takes no lock and elects no leader, on the same assumption the recurrence
 * job rests on (adr/0025): both compose files run one {@code app} service with
 * one replica. A second instance would double-fetch every feed, which is
 * wasteful rather than wrong - the refresh replaces a feed's rows wholesale, so
 * two runs converge on the same state.
 *
 * <p>Like a recurrence run, this has no caller: it must not consult
 * {@code CurrentUserProvider}, because nobody is calling.
 */
@Component
@ConditionalOnProperty(name = "app.calendar.enabled", havingValue = "true", matchIfMissing = true)
class CalendarSyncJob {

    private static final Logger log = LoggerFactory.getLogger(CalendarSyncJob.class);

    private final CalendarSyncService sync;

    CalendarSyncJob(CalendarSyncService sync) {
        this.sync = sync;
    }

    @Scheduled(cron = "${app.calendar.cron}", zone = "${app.timezone:Asia/Singapore}")
    void scheduled() {
        CalendarSyncService.Outcome outcome = sync.syncAll();
        log.info("Calendar refresh: {} feeds, {} refreshed, {} failed",
                outcome.feeds(), outcome.refreshed(), outcome.failed());
    }
}
