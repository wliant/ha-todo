package com.hatodo.calendar;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CalendarFeedRepository extends JpaRepository<CalendarFeed, UUID> {

    /** The caller's own feeds. There is no screen that lists anybody else's. */
    @Query("SELECT f FROM CalendarFeed f WHERE f.owner.id = :ownerId ORDER BY LOWER(f.label) ASC")
    List<CalendarFeed> findOwnedBy(@Param("ownerId") UUID ownerId);

    /**
     * A feed the caller owns, or empty - which the service turns into 404
     * rather than 403, because a feed they were not granted must not be
     * discoverable by its id.
     */
    @Query("SELECT f FROM CalendarFeed f WHERE f.id = :feedId AND f.owner.id = :ownerId")
    Optional<CalendarFeed> findOwnedById(@Param("feedId") UUID feedId,
                                        @Param("ownerId") UUID ownerId);

    /**
     * Explicit rather than derived, for the reason the list repository gives:
     * the unique index is on {@code (owner_id, lower(label))} and a derived
     * query would compare with upper(), so a label this accepted could still be
     * refused by the database.
     */
    @Query("""
            SELECT COUNT(f) > 0 FROM CalendarFeed f
            WHERE f.owner.id = :ownerId AND LOWER(f.label) = LOWER(:label)
            AND (:excluding IS NULL OR f.id <> :excluding)
            """)
    boolean existsWithLabel(@Param("ownerId") UUID ownerId, @Param("label") String label,
                            @Param("excluding") UUID excluding);

    /** The digest, never the address: this is the one check that touches it. */
    @Query("""
            SELECT COUNT(f) > 0 FROM CalendarFeed f
            WHERE f.owner.id = :ownerId AND f.urlDigest = :digest
            AND (:excluding IS NULL OR f.id <> :excluding)
            """)
    boolean existsWithUrl(@Param("ownerId") UUID ownerId, @Param("digest") String digest,
                          @Param("excluding") UUID excluding);

    /**
     * Every feed the caller may see, their own included - the grant join being
     * the authorization, in SQL.
     *
     * <p>Used for the staleness notice, which is about other members' feeds as
     * much as the caller's: an empty Thursday because Ben's calendar stopped
     * refreshing is Ana's problem too.
     */
    @Query("""
            SELECT f FROM CalendarFeed f
            JOIN FETCH f.owner
            WHERE EXISTS (
                SELECT 1 FROM CalendarFeedAccess a
                WHERE a.feed = f AND a.user.id = :userId
            )
            ORDER BY LOWER(f.label) ASC
            """)
    List<CalendarFeed> findVisibleFor(@Param("userId") UUID userId);

    /** Every feed in the deployment: the poller's one read. */
    @Query("SELECT f.id FROM CalendarFeed f ORDER BY f.lastAttemptAt ASC NULLS FIRST")
    List<UUID> findAllIdsOldestFirst();
}
