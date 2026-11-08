package com.hatodo.data;

import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DataListInteractionRepository
        extends JpaRepository<DataListInteractionDay, DataListInteractionDayId> {

    /**
     * Counts a visit, unless this bucket was written within the window.
     *
     * <p>The {@code WHERE} on the {@code DO UPDATE} is the dedup window, read
     * from the row being written - which is what makes this independent of
     * {@code list_user_state} and so reusable by collections, which have no
     * state row at all (adr/0041). A repeat visit inside the window takes no
     * row lock and writes no WAL.
     *
     * <p>A collection has no recency row to consult, which is the whole reason
     * the window is expressed inside this statement rather than beside it.
     *
     * @return 1 if the visit counted, 0 if it fell inside the window
     */
    @Modifying
    @Query(value = """
            INSERT INTO data_list_interaction_day (data_list_id, user_id, on_day, hits, last_at)
            VALUES (:listId, :userId, :onDay, 1, now())
            ON CONFLICT (data_list_id, user_id, on_day) DO UPDATE
               SET hits = data_list_interaction_day.hits + 1, last_at = now()
             WHERE data_list_interaction_day.last_at < now() - make_interval(mins => :windowMinutes)
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
            DELETE FROM data_list_interaction_day
            WHERE user_id = :userId AND on_day < :before
            """, nativeQuery = true)
    int prune(@Param("userId") UUID userId, @Param("before") LocalDate before);
}
