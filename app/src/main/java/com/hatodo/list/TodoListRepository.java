package com.hatodo.list;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TodoListRepository extends JpaRepository<TodoList, UUID> {

    /**
     * The lists a member may see, filtered and ordered in SQL.
     *
     * <p>Filtering here rather than in Java is the whole point: a predicate
     * applied after fetching every row passes every unit test and leaks every
     * list. The owner has a {@code list_access} row of their own, so the join
     * alone answers "can I see it" without a separate owner check.
     *
     * @param scope {@code owned}, {@code shared}, or anything else for all
     */
    @Query("""
            SELECT l FROM TodoList l
            WHERE EXISTS (
                SELECT 1 FROM ListAccess a
                WHERE a.list = l AND a.user.id = :userId
            )
            AND (:query IS NULL OR LOWER(l.name) LIKE LOWER(CONCAT('%', CAST(:query AS string), '%')))
            AND (:icon IS NULL OR l.icon = :icon)
            AND (
                :scope = 'all'
                OR (:scope = 'owned' AND l.owner.id = :userId)
                OR (:scope = 'shared' AND l.owner.id <> :userId)
            )
            AND (
                :pinnedOnly = FALSE
                OR EXISTS (
                    SELECT 1 FROM ListUserState s
                    WHERE s.list = l AND s.user.id = :userId AND s.pinnedAt IS NOT NULL
                )
            )
            ORDER BY LOWER(l.name) ASC
            """)
    List<TodoList> findVisible(@Param("userId") UUID userId,
                               @Param("query") String query,
                               @Param("icon") ListIcon icon,
                               @Param("scope") String scope,
                               @Param("pinnedOnly") boolean pinnedOnly);

    /**
     * The sidebar's one group: every list the member can see, ordered by how
     * they use it (0012 R98, R100, R101).
     *
     * <p>Native, because of the {@code NULLS LAST} and the aggregate join -
     * both of which JPQL cannot say.
     *
     * <p>Three things here are load-bearing:
     * <ul>
     *   <li>The {@code JOIN} on {@code list_access} is the access filter, in
     *       SQL, as every list read is. The owner holds a grant of their own.
     *   <li>Both other joins are {@code LEFT}, which is R98: a list with no
     *       state row and no buckets still comes back. The old "recent" query
     *       required a state row, so a list nobody had opened on this account -
     *       including one just created, and every list somebody else shared -
     *       was absent from the sidebar entirely.
     *   <li>{@code (s.pinned_at IS NOT NULL) DESC} is R101. A boolean sorts
     *       false before true ascending in Postgres, so pinned-first is
     *       {@code DESC}.
     * </ul>
     *
     * @param since the first day of the frequency window, in the household's
     *              zone. A parameter of the query and deliberately not of the
     *              endpoint, for adr/0040's reason: a window a caller supplies
     *              is a window that drifts.
     */
    @Query(value = """
            SELECT l.* FROM todo_list l
            JOIN list_access a ON a.list_id = l.id AND a.user_id = :userId
            LEFT JOIN list_user_state s ON s.list_id = l.id AND s.user_id = :userId
            LEFT JOIN (
                SELECT list_id, SUM(hits) AS hits
                FROM list_interaction_day
                WHERE user_id = :userId AND on_day >= :since
                GROUP BY list_id
            ) f ON f.list_id = l.id
            ORDER BY (s.pinned_at IS NOT NULL) DESC,
                     COALESCE(f.hits, 0) DESC,
                     s.last_interacted_at DESC NULLS LAST,
                     LOWER(l.name) ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<TodoList> findForSidebar(@Param("userId") UUID userId,
                                  @Param("since") LocalDate since,
                                  @Param("limit") int limit);

    /** A list the caller may see, or empty - which the service turns into 404. */
    @Query("""
            SELECT l FROM TodoList l
            WHERE l.id = :listId
            AND EXISTS (
                SELECT 1 FROM ListAccess a
                WHERE a.list = l AND a.user.id = :userId
            )
            """)
    Optional<TodoList> findVisibleById(@Param("listId") UUID listId, @Param("userId") UUID userId);

    /**
     * Explicit rather than derived: the unique index is on
     * {@code (owner_id, lower(name))}, and a derived query would compare with
     * upper() instead - so a name this accepted could still be refused by the
     * database.
     */
    @Query("""
            SELECT COUNT(l) > 0 FROM TodoList l
            WHERE l.owner.id = :ownerId AND LOWER(l.name) = LOWER(:name)
            """)
    boolean existsWithName(@Param("ownerId") UUID ownerId, @Param("name") String name);

    @Query("""
            SELECT COUNT(l) > 0 FROM TodoList l
            WHERE l.owner.id = :ownerId AND LOWER(l.name) = LOWER(:name) AND l.id <> :excluding
            """)
    boolean existsOtherWithName(@Param("ownerId") UUID ownerId,
                                @Param("name") String name,
                                @Param("excluding") UUID excluding);
}
