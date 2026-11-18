package com.hatodo.tracker;

import com.hatodo.list.ListIcon;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TrackerRepository extends JpaRepository<Tracker, UUID> {

    /**
     * The trackers a member may see, filtered and ordered in SQL.
     *
     * <p>The access guard is the driving predicate, not a filter applied
     * afterwards, for the reason the other two repositories give: a predicate
     * applied after fetching every row passes every unit test and leaks every
     * tracker. The owner holds a {@code tracker_access} row of their own, so
     * the EXISTS alone answers "can I see it".
     *
     * @param scope {@code owned}, {@code shared}, or anything else for all
     */
    @Query("""
            SELECT t FROM Tracker t
            WHERE EXISTS (
                SELECT 1 FROM TrackerAccess a
                WHERE a.tracker = t AND a.user.id = :userId
            )
            AND (:query IS NULL OR LOWER(t.name) LIKE LOWER(CONCAT('%', CAST(:query AS string), '%')))
            AND (:icon IS NULL OR t.icon = :icon)
            AND (
                :scope = 'all'
                OR (:scope = 'owned' AND t.owner.id = :userId)
                OR (:scope = 'shared' AND t.owner.id <> :userId)
            )
            ORDER BY LOWER(t.name) ASC
            """)
    List<Tracker> findVisible(@Param("userId") UUID userId,
                              @Param("query") String query,
                              @Param("icon") ListIcon icon,
                              @Param("scope") String scope);

    /**
     * The sidebar's Trackers group: what the member can see and has
     * <strong>not</strong> pinned, ordered by how often they open each one
     * (0019 R170, following 0012 R98-R100).
     *
     * <p>The outer join is <strong>LEFT</strong> and must stay so: a tracker
     * created a minute ago has no interaction bucket, and an inner join would
     * quietly restore the only-what-you-opened behaviour the Recent section was
     * dropped for.
     *
     * @param since the first day of the frequency window, in the household's
     *              zone. Supplied by the service, never by the caller.
     */
    @Query(value = """
            SELECT t.* FROM tracker t
            JOIN tracker_access a ON a.tracker_id = t.id AND a.user_id = :userId
            LEFT JOIN tracker_user_state s
                   ON s.tracker_id = t.id AND s.user_id = :userId
            LEFT JOIN (
                SELECT tracker_id, SUM(hits) AS hits, MAX(last_at) AS last_at
                FROM tracker_interaction_day
                WHERE user_id = :userId AND on_day >= :since
                GROUP BY tracker_id
            ) f ON f.tracker_id = t.id
            WHERE s.pinned_at IS NULL
            ORDER BY COALESCE(f.hits, 0) DESC,
                     f.last_at DESC NULLS LAST,
                     LOWER(t.name) ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<Tracker> findForSidebar(@Param("userId") UUID userId,
                                 @Param("since") LocalDate since,
                                 @Param("limit") int limit);

    /**
     * Every tracker the member has pinned.
     *
     * <p>Uncapped and by name: the pinned section scrolls rather than
     * truncating, and its three halves are merged on the client from three
     * requests with no shared clock to interleave on.
     */
    @Query("""
            SELECT s.tracker FROM TrackerUserState s
            WHERE s.user.id = :userId AND s.pinnedAt IS NOT NULL
            AND EXISTS (
                SELECT 1 FROM TrackerAccess a WHERE a.tracker = s.tracker AND a.user.id = :userId
            )
            ORDER BY LOWER(s.tracker.name) ASC
            """)
    List<Tracker> findPinnedForSidebar(@Param("userId") UUID userId);

    /** A tracker the caller may see, or empty - which the service turns into 404. */
    @Query("""
            SELECT t FROM Tracker t
            WHERE t.id = :trackerId
            AND EXISTS (
                SELECT 1 FROM TrackerAccess a
                WHERE a.tracker = t AND a.user.id = :userId
            )
            """)
    Optional<Tracker> findVisibleById(@Param("trackerId") UUID trackerId,
                                      @Param("userId") UUID userId);

    /**
     * Every daily tracker the member can see, for the agenda's nudge (R171).
     *
     * <p>Only DAY: a monthly tracker nudged every day would be a reproach for
     * four weeks, which is the opposite of a reminder.
     */
    @Query("""
            SELECT t FROM Tracker t
            WHERE t.period = com.hatodo.tracker.TrackerPeriod.DAY
            AND EXISTS (
                SELECT 1 FROM TrackerAccess a
                WHERE a.tracker = t AND a.user.id = :userId
            )
            ORDER BY LOWER(t.name) ASC
            """)
    List<Tracker> findDailyVisible(@Param("userId") UUID userId);

    /**
     * Explicit rather than derived, for the reason the other two give: the
     * unique index is on {@code (owner_id, lower(name))} and a derived query
     * would compare with upper(), so a name this accepted could still be
     * refused by the database.
     */
    @Query("""
            SELECT COUNT(t) > 0 FROM Tracker t
            WHERE t.owner.id = :ownerId AND LOWER(t.name) = LOWER(:name)
            """)
    boolean existsWithName(@Param("ownerId") UUID ownerId, @Param("name") String name);

    @Query("""
            SELECT COUNT(t) > 0 FROM Tracker t
            WHERE t.owner.id = :ownerId AND LOWER(t.name) = LOWER(:name) AND t.id <> :excluding
            """)
    boolean existsOtherWithName(@Param("ownerId") UUID ownerId,
                                @Param("name") String name,
                                @Param("excluding") UUID excluding);
}
