package com.hatodo.list;

import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ListInteractionRepository
        extends JpaRepository<ListInteractionDay, ListInteractionDayId> {

    /**
     * Counts a visit, unless this bucket was written within the window.
     *
     * <p>The {@code WHERE} on the {@code DO UPDATE} is the dedup window, read
     * from the row being written - which is what makes this independent of
     * {@code list_user_state} and so reusable by collections, which have no
     * state row at all (adr/0041). A repeat visit inside the window takes no
     * row lock and writes no WAL.
     *
     * <p>Deliberately **not** coupled to the recency upsert in
     * {@link ListUserStateRepository#touch}, even though both express fifteen
     * minutes. They can legitimately disagree: at 00:02 the recency write
     * no-ops while this one creates a new day's bucket, because a visit after
     * midnight is a new day's visit. Making either read the other reintroduces
     * an ordering dependency between two statements in one method.
     *
     * @return 1 if the visit counted, 0 if it fell inside the window
     */
    @Modifying
    @Query(value = """
            INSERT INTO list_interaction_day (list_id, user_id, on_day, hits, last_at)
            VALUES (:listId, :userId, :onDay, 1, now())
            ON CONFLICT (list_id, user_id, on_day) DO UPDATE
               SET hits = list_interaction_day.hits + 1, last_at = now()
             WHERE list_interaction_day.last_at < now() - make_interval(mins => :windowMinutes)
            """, nativeQuery = true)
    int record(@Param("listId") UUID listId,
               @Param("userId") UUID userId,
               @Param("onDay") LocalDate onDay,
               @Param("windowMinutes") int windowMinutes);

    /**
     * Drops this member's buckets older than the window, so the rows read are
     * the rows kept and nothing accumulates.
     *
     * <p>Scoped to the member rather than to (member, list): the buckets that
     * need dropping belong to the lists they **stopped** opening, which are
     * exactly the ones {@link #record} will never be called for again.
     */
    @Modifying
    @Query(value = """
            DELETE FROM list_interaction_day
            WHERE user_id = :userId AND on_day < :before
            """, nativeQuery = true)
    int prune(@Param("userId") UUID userId, @Param("before") LocalDate before);
}
