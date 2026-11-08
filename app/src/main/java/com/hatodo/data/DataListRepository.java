package com.hatodo.data;

import com.hatodo.list.ListIcon;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DataListRepository extends JpaRepository<DataList, UUID> {

    /**
     * The collections a member may see, filtered and ordered in SQL.
     *
     * <p>Filtering here rather than in Java is the whole point, and the reason
     * is the one {@code TodoListRepository} gives: a predicate applied after
     * fetching every row passes every unit test and leaks every collection. The
     * owner holds a {@code data_list_access} row of their own, so the join
     * alone answers "can I see it".
     *
     * @param scope {@code owned}, {@code shared}, or anything else for all
     */
    @Query("""
            SELECT l FROM DataList l
            WHERE EXISTS (
                SELECT 1 FROM DataListAccess a
                WHERE a.list = l AND a.user.id = :userId
            )
            AND (:query IS NULL OR LOWER(l.name) LIKE LOWER(CONCAT('%', CAST(:query AS string), '%')))
            AND (:icon IS NULL OR l.icon = :icon)
            AND (
                :scope = 'all'
                OR (:scope = 'owned' AND l.owner.id = :userId)
                OR (:scope = 'shared' AND l.owner.id <> :userId)
            )
            ORDER BY LOWER(l.name) ASC
            """)
    List<DataList> findVisible(@Param("userId") UUID userId,
                               @Param("query") String query,
                               @Param("icon") ListIcon icon,
                               @Param("scope") String scope);

    /**
     * The sidebar's collections group: every collection the member can see,
     * ordered by how they use it (0012 R98, R100).
     *
     * <p>The todo-list query's shape minus the pin and recency terms, because a
     * collection has neither (adr/0029, and 0012 R101 accepts it). The
     * tie-break is {@code MAX(last_at)} from the bucket table - the only
     * recency a collection has, and only a tie-break.
     *
     * @param since the first day of the frequency window, in the household's
     *              zone. Supplied by the service, never by the caller.
     */
    @Query(value = """
            SELECT l.* FROM data_list l
            JOIN data_list_access a ON a.data_list_id = l.id AND a.user_id = :userId
            LEFT JOIN (
                SELECT data_list_id, SUM(hits) AS hits, MAX(last_at) AS last_at
                FROM data_list_interaction_day
                WHERE user_id = :userId AND on_day >= :since
                GROUP BY data_list_id
            ) f ON f.data_list_id = l.id
            ORDER BY COALESCE(f.hits, 0) DESC,
                     f.last_at DESC NULLS LAST,
                     LOWER(l.name) ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<DataList> findForSidebar(@Param("userId") UUID userId,
                                  @Param("since") LocalDate since,
                                  @Param("limit") int limit);

    /** A collection the caller may see, or empty - which the service turns into 404. */
    @Query("""
            SELECT l FROM DataList l
            WHERE l.id = :listId
            AND EXISTS (
                SELECT 1 FROM DataListAccess a
                WHERE a.list = l AND a.user.id = :userId
            )
            """)
    Optional<DataList> findVisibleById(@Param("listId") UUID listId, @Param("userId") UUID userId);

    /**
     * Explicit rather than derived, for the reason the list repository gives:
     * the unique index is on {@code (owner_id, lower(name))} and a derived
     * query would compare with upper(), so a name this accepted could still be
     * refused by the database.
     */
    @Query("""
            SELECT COUNT(l) > 0 FROM DataList l
            WHERE l.owner.id = :ownerId AND LOWER(l.name) = LOWER(:name)
            """)
    boolean existsWithName(@Param("ownerId") UUID ownerId, @Param("name") String name);

    @Query("""
            SELECT COUNT(l) > 0 FROM DataList l
            WHERE l.owner.id = :ownerId AND LOWER(l.name) = LOWER(:name) AND l.id <> :excluding
            """)
    boolean existsOtherWithName(@Param("ownerId") UUID ownerId,
                                @Param("name") String name,
                                @Param("excluding") UUID excluding);
}
