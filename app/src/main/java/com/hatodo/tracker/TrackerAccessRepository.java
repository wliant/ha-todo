package com.hatodo.tracker;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TrackerAccessRepository
        extends JpaRepository<TrackerAccess, TrackerAccessId> {

    @Query("""
            SELECT a FROM TrackerAccess a
            WHERE a.tracker.id = :trackerId ORDER BY LOWER(a.user.displayName)
            """)
    List<TrackerAccess> findForTracker(@Param("trackerId") UUID trackerId);

    @Query("SELECT a FROM TrackerAccess a WHERE a.tracker.id = :trackerId AND a.user.id = :userId")
    Optional<TrackerAccess> find(@Param("trackerId") UUID trackerId, @Param("userId") UUID userId);

    @Query("""
            SELECT COUNT(a) > 0 FROM TrackerAccess a
            WHERE a.tracker.id = :trackerId AND a.user.id = :userId
            """)
    boolean exists(@Param("trackerId") UUID trackerId, @Param("userId") UUID userId);
}
