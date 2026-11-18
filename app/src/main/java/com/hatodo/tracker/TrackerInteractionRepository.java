package com.hatodo.tracker;

import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TrackerInteractionRepository
        extends JpaRepository<TrackerInteractionDay, TrackerInteractionDayId> {

    /**
     * Counts a visit, unless this bucket was written within the window.
     *
     * <p>The {@code WHERE} on the {@code DO UPDATE} is the dedup window, read
     * from the row being written - which is what lets a kind with no state row
     * have frequency without one (adr/0041). A repeat visit inside the window
     * takes no row lock and writes no WAL.
     *
     * @return 1 if the visit counted, 0 if it fell inside the window
     */
    @Modifying
    @Query(value = """
            INSERT INTO tracker_interaction_day (tracker_id, user_id, on_day, hits, last_at)
            VALUES (:trackerId, :userId, :onDay, 1, now())
            ON CONFLICT (tracker_id, user_id, on_day) DO UPDATE
               SET hits = tracker_interaction_day.hits + 1, last_at = now()
             WHERE tracker_interaction_day.last_at < now() - make_interval(mins => :windowMinutes)
            """, nativeQuery = true)
    int record(@Param("trackerId") UUID trackerId,
               @Param("userId") UUID userId,
               @Param("onDay") LocalDate onDay,
               @Param("windowMinutes") int windowMinutes);

    /**
     * Drops this member's buckets older than the window, so the rows read are
     * the rows kept and nothing accumulates.
     *
     * <p>Scoped to the member rather than to (member, tracker): the buckets
     * worth dropping belong to the trackers they <strong>stopped</strong>
     * opening, which are exactly the ones {@link #record} will never be called
     * for again.
     */
    @Modifying
    @Query(value = """
            DELETE FROM tracker_interaction_day
            WHERE user_id = :userId AND on_day < :before
            """, nativeQuery = true)
    int prune(@Param("userId") UUID userId, @Param("before") LocalDate before);
}
