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

    /**
     * One list's items, whole, in the order a member arranged them.
     *
     * <p>Unfiltered: narrowing a list by status, assignee, due date or cost is
     * the browser's job, over this one payload (0013, adr/0044). The `status`
     * parameter this query used to take is gone with the client that sent it -
     * a predicate implemented on both sides of the wire is how two notions of
     * "done" start to disagree.
     *
     * <p>The generated `closed` column still has one reader, in
     * {@link #findScheduledFor} and its neighbours: the dashboard reads across
     * every list a member can see and cannot fetch them all.
     */
    @Query("SELECT i FROM TodoItem i WHERE i.list.id = :listId ORDER BY i.position ASC")
    List<TodoItem> findForList(@Param("listId") UUID listId);

    @Query("SELECT COALESCE(MAX(i.position), -1) FROM TodoItem i WHERE i.list.id = :listId")
    int highestPosition(@Param("listId") UUID listId);

    /**
     * The per-list leaderboard (R23), summed over the items themselves.
     *
     * <p>No stored counter: one would be a second source of truth, and it
     * disagrees with the items the first time a delete path forgets to
     * decrement it.
     *
     * <p>Not {@code completedBy IS NOT NULL} on its own: a reopened item keeps
     * who completed it (R49) while its points are taken back, so that predicate
     * matched a group whose whole sum was null - which is a 500 on this
     * endpoint, not merely a wrong number. The COALESCE is the belt to that
     * braces. Standing credit is what this answers; the activity feed asks a
     * different question and keys on completedAt instead.
     *
     * <p>And not {@code closed = TRUE} either, which is the trap 0015 laid.
     * {@code closed} now means "no longer work", which a lapsed item also is -
     * so an item completed, reopened and then lapsed would be matched here,
     * contributing a nonsense zero-point row for a member who earned nothing.
     * The test has to be for a <em>standing completion</em>, spelled out:
     * completed, not since reopened, and not since lapsed. That is
     * {@code TodoItem.isCompleted()}, in JPQL. See adr/0048.
     */
    @Query("""
            SELECT i.completedBy.id, i.completedBy.displayName, COALESCE(SUM(i.pointsEarned), 0)
            FROM TodoItem i
            WHERE i.list.id = :listId AND i.completedBy IS NOT NULL
              AND i.completedAt IS NOT NULL AND i.reopenedAt IS NULL AND i.lapsedAt IS NULL
            GROUP BY i.completedBy.id, i.completedBy.displayName
            ORDER BY COALESCE(SUM(i.pointsEarned), 0) DESC, i.completedBy.displayName ASC
            """)
    List<Object[]> pointsByMember(@Param("listId") UUID listId);

    /**
     * Who completed how much on one list inside a window (R43).
     *
     * <p>Keyed on {@code completedAt}, deliberately not on {@code closed}: a
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
            WHERE i.closed = FALSE
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
            WHERE i.closed = FALSE AND i.dueAt IS NULL
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
            WHERE i.closed = FALSE AND i.dueAt IS NULL
              AND (:mineOnly = FALSE OR i.assignee.id = :userId)
              AND EXISTS (
                  SELECT 1 FROM ListAccess a WHERE a.list = i.list AND a.user.id = :userId
              )
            """)
    long countUndatedFor(@Param("userId") UUID userId, @Param("mineOnly") boolean mineOnly);

    /**
     * The open work whose day has already gone (0011 R88-R93).
     *
     * <p>The complement of {@link #findUndatedFor}: that one holds the items
     * with no date at all, this one the items with a date that has passed, and
     * between them they must partition the open work without overlapping - so
     * the redundant {@code dueAt IS NOT NULL} stays, as the sentence that says
     * so.
     *
     * <p>{@code before} is the start of today in the household's zone, decided
     * by the service and never sent by a caller: the agenda can be navigated,
     * and a bound a client supplies is a bound the period on screen can leak
     * into. See adr/0040.
     *
     * <p>Oldest first, unlike the undated panel's by-list order. The page is
     * capped, so the part a member sees should be the part that has been
     * waiting longest.
     */
    @Query("""
            SELECT i FROM TodoItem i
            WHERE i.closed = FALSE
              AND i.dueAt IS NOT NULL AND i.dueAt < :before
              AND (:mineOnly = FALSE OR i.assignee.id = :userId)
              AND EXISTS (
                  SELECT 1 FROM ListAccess a WHERE a.list = i.list AND a.user.id = :userId
              )
            ORDER BY i.dueAt ASC, i.list.name ASC, i.position ASC
            """)
    List<TodoItem> findOverdueFor(@Param("userId") UUID userId, @Param("before") Instant before,
                                  @Param("mineOnly") boolean mineOnly, Pageable limit);

    /**
     * How much is actually late, which the page may not be able to show.
     *
     * <p>Counted apart for the reason the undated panel's total is, and more
     * sharply: the first load after this ships holds every item the household
     * ever dated and never finished, and a strip that says "3" because it drew
     * three is the one thing this feature cannot afford to be.
     */
    @Query("""
            SELECT COUNT(i) FROM TodoItem i
            WHERE i.closed = FALSE
              AND i.dueAt IS NOT NULL AND i.dueAt < :before
              AND (:mineOnly = FALSE OR i.assignee.id = :userId)
              AND EXISTS (
                  SELECT 1 FROM ListAccess a WHERE a.list = i.list AND a.user.id = :userId
              )
            """)
    long countOverdueFor(@Param("userId") UUID userId, @Param("before") Instant before,
                         @Param("mineOnly") boolean mineOnly);

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
