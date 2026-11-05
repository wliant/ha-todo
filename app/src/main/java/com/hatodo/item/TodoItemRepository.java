package com.hatodo.item;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TodoItemRepository extends JpaRepository<TodoItem, UUID> {

    /*
     * "Open" and "done" test the generated `done` column rather than
     * completed_at, because a reopened item keeps its completion time (R49,
     * adr/0024). Written as `i.done` and not the underlying expression so the
     * partial indexes defined on it stay usable.
     */

    @Query("""
            SELECT i FROM TodoItem i
            WHERE i.list.id = :listId
            AND (:status = 'all'
                 OR (:status = 'open' AND i.done = FALSE)
                 OR (:status = 'done' AND i.done = TRUE))
            ORDER BY i.position ASC
            """)
    List<TodoItem> findForList(@Param("listId") UUID listId, @Param("status") String status);

    @Query("SELECT COALESCE(MAX(i.position), -1) FROM TodoItem i WHERE i.list.id = :listId")
    int highestPosition(@Param("listId") UUID listId);

    /**
     * The per-list leaderboard (R23), summed over the items themselves.
     *
     * <p>No stored counter: one would be a second source of truth, and it
     * disagrees with the items the first time a delete path forgets to
     * decrement it.
     */
    @Query("""
            SELECT i.completedBy.id, i.completedBy.displayName, SUM(i.pointsEarned)
            FROM TodoItem i
            WHERE i.list.id = :listId AND i.completedBy IS NOT NULL
            GROUP BY i.completedBy.id, i.completedBy.displayName
            ORDER BY SUM(i.pointsEarned) DESC, i.completedBy.displayName ASC
            """)
    List<Object[]> pointsByMember(@Param("listId") UUID listId);

    /** Used when a grant is revoked: an assignee must be able to see the list. */
    @Query("SELECT i FROM TodoItem i WHERE i.list.id = :listId AND i.assignee.id = :userId")
    List<TodoItem> findAssignedTo(@Param("listId") UUID listId, @Param("userId") UUID userId);

    Optional<TodoItem> findById(UUID id);
}
