package com.hatodo.item;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
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
     *
     * <p>{@code done = TRUE}, not {@code completedBy IS NOT NULL}: a reopened
     * item keeps who completed it (R49) while its points are taken back, so the
     * older predicate matched a group whose whole sum was null - which is a 500
     * on this endpoint, not merely a wrong number. The COALESCE is the belt to
     * that braces. Standing credit is what this answers; the activity feed asks
     * a different question and keys on completedAt instead.
     */
    @Query("""
            SELECT i.completedBy.id, i.completedBy.displayName, COALESCE(SUM(i.pointsEarned), 0)
            FROM TodoItem i
            WHERE i.list.id = :listId AND i.done = TRUE AND i.completedBy IS NOT NULL
            GROUP BY i.completedBy.id, i.completedBy.displayName
            ORDER BY COALESCE(SUM(i.pointsEarned), 0) DESC, i.completedBy.displayName ASC
            """)
    List<Object[]> pointsByMember(@Param("listId") UUID listId);

    /**
     * Who completed how much on one list inside a window (R43).
     *
     * <p>Keyed on {@code completedAt}, deliberately not on {@code done}: a
     * completion that was later reopened still happened, and R49 kept the
     * record precisely so it could be seen. The points are COALESCEd because
     * exactly those rows have had theirs taken back.
     */
    @Query("""
            SELECT i.completedBy.id, i.completedBy.displayName,
                   i.completedBy.avatarImage.id, COUNT(i),
                   COALESCE(SUM(i.pointsEarned), 0)
            FROM TodoItem i
            WHERE i.list.id = :listId AND i.completedBy IS NOT NULL
              AND i.completedAt >= :from AND i.completedAt < :to
            GROUP BY i.completedBy.id, i.completedBy.displayName,
                     i.completedBy.avatarImage.id
            ORDER BY COUNT(i) DESC, i.completedBy.displayName ASC
            """)
    List<Object[]> activityByMember(@Param("listId") UUID listId,
                                    @Param("from") Instant from, @Param("to") Instant to);

    /** The completions themselves, newest first. Capped by the caller. */
    @Query("""
            SELECT i FROM TodoItem i
            WHERE i.list.id = :listId AND i.completedAt >= :from AND i.completedAt < :to
            ORDER BY i.completedAt DESC
            """)
    List<TodoItem> activityEvents(@Param("listId") UUID listId,
                                  @Param("from") Instant from, @Param("to") Instant to,
                                  Pageable limit);

    /**
     * Everything due in a window, across every list the caller can see (R44).
     *
     * <p>The EXISTS is the same guard TodoListRepository.findVisibleById uses,
     * and it is in SQL for the reason that one is: a predicate applied after
     * fetching passes every unit test and leaks the household's private lists
     * onto one member's dashboard. This is the first query where getting it
     * wrong would leak several lists at once.
     *
     * <p>Half-open on [from, to), with both bounds supplied by the client -
     * only the browser knows where this member's midnight is.
     */
    @Query("""
            SELECT i FROM TodoItem i
            WHERE i.done = FALSE
              AND i.dueAt >= :from AND i.dueAt < :to
              AND (:mineOnly = FALSE OR i.assignee.id = :userId)
              AND EXISTS (
                  SELECT 1 FROM ListAccess a WHERE a.list = i.list AND a.user.id = :userId
              )
            ORDER BY i.dueAt ASC, i.list.name ASC, i.position ASC
            """)
    List<TodoItem> findScheduledFor(@Param("userId") UUID userId,
                                    @Param("from") Instant from, @Param("to") Instant to,
                                    @Param("mineOnly") boolean mineOnly, Pageable limit);

    /**
     * The items nobody has dated (R45), shown beside every range.
     *
     * <p>Separate from the ranged query rather than folded into it: the panel
     * sits beside all three views, so sharing their key would refetch it on
     * every tab change.
     */
    @Query("""
            SELECT i FROM TodoItem i
            WHERE i.done = FALSE AND i.dueAt IS NULL
              AND (:mineOnly = FALSE OR i.assignee.id = :userId)
              AND EXISTS (
                  SELECT 1 FROM ListAccess a WHERE a.list = i.list AND a.user.id = :userId
              )
            ORDER BY i.list.name ASC, i.position ASC
            """)
    List<TodoItem> findUndatedFor(@Param("userId") UUID userId,
                                  @Param("mineOnly") boolean mineOnly, Pageable limit);

    /**
     * How many undated items there really are.
     *
     * <p>Counted apart from the page, because after adr/0022's backfill every
     * list declares a due date and nobody has filled most of them in - so the
     * panel is capped and has to be able to say "and 37 more".
     */
    @Query("""
            SELECT COUNT(i) FROM TodoItem i
            WHERE i.done = FALSE AND i.dueAt IS NULL
              AND (:mineOnly = FALSE OR i.assignee.id = :userId)
              AND EXISTS (
                  SELECT 1 FROM ListAccess a WHERE a.list = i.list AND a.user.id = :userId
              )
            """)
    long countUndatedFor(@Param("userId") UUID userId, @Param("mineOnly") boolean mineOnly);

    /**
     * Whether a rule has already produced this occurrence (R42).
     *
     * <p>Asked before inserting rather than catching the unique index's
     * violation: Hibernate defers the insert to flush, so the violation
     * arrives at commit - outside any per-occurrence catch, and after the loop
     * has finished. The index stays as the backstop for two runs genuinely
     * racing, and that case fails the rule's whole transaction, which the
     * runner records.
     */
    boolean existsByRuleIdAndOccurrenceOn(UUID ruleId, LocalDate occurrenceOn);

    /** Used when a grant is revoked: an assignee must be able to see the list. */
    @Query("SELECT i FROM TodoItem i WHERE i.list.id = :listId AND i.assignee.id = :userId")
    List<TodoItem> findAssignedTo(@Param("listId") UUID listId, @Param("userId") UUID userId);

    Optional<TodoItem> findById(UUID id);
}
