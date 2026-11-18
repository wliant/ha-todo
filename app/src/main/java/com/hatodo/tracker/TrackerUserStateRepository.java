package com.hatodo.tracker;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TrackerUserStateRepository
        extends JpaRepository<TrackerUserState, TrackerUserStateId> {

    @Query("SELECT s FROM TrackerUserState s WHERE s.tracker.id = :trackerId AND s.user.id = :userId")
    Optional<TrackerUserState> find(@Param("trackerId") UUID trackerId, @Param("userId") UUID userId);
}
