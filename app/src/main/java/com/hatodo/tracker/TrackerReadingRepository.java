package com.hatodo.tracker;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TrackerReadingRepository extends JpaRepository<TrackerReading, UUID> {

    /**
     * Every reading in a window, oldest first.
     *
     * <p>Ordered by bucket and then by {@code recordedAt}, which is what lets
     * {@link Rollup#LATEST} simply take the last of what it is given - the
     * ordering lives here and in nothing else.
     *
     * <p>Capped by the caller. That cap is a safety bound rather than
     * pagination: hitting it is the threshold for moving the fold into SQL
     * (adr/0052).
     */
    @Query("""
            SELECT r FROM TrackerReading r
            WHERE r.tracker.id = :trackerId
            AND r.periodStart >= :from AND r.periodStart < :to
            ORDER BY r.periodStart ASC, r.recordedAt ASC
            """)
    List<TrackerReading> findInWindow(@Param("trackerId") UUID trackerId,
                                      @Param("from") LocalDate from,
                                      @Param("to") LocalDate to,
                                      Limit limit);

    /** The readings table: newest first, which is the opposite of the fold's order. */
    @Query("""
            SELECT r FROM TrackerReading r
            WHERE r.tracker.id = :trackerId
            ORDER BY r.periodStart DESC, r.recordedAt DESC
            """)
    List<TrackerReading> findForTracker(@Param("trackerId") UUID trackerId, Limit limit);

    /**
     * One reading with its tracker already loaded, so a single access question
     * answers everything the caller then does with it.
     */
    @Query("SELECT r FROM TrackerReading r JOIN FETCH r.tracker WHERE r.id = :id")
    Optional<TrackerReading> findWithTracker(@Param("id") UUID id);

    @Query("SELECT COUNT(r) > 0 FROM TrackerReading r WHERE r.tracker.id = :trackerId")
    boolean existsForTracker(@Param("trackerId") UUID trackerId);

    @Query("SELECT COUNT(r) FROM TrackerReading r WHERE r.tracker.id = :trackerId")
    long countForTracker(@Param("trackerId") UUID trackerId);

    /**
     * Which of these trackers the member has already recorded into for a given
     * bucket - the agenda nudge's question, asked once rather than per tracker
     * (R171).
     *
     * <p>Reads the member's own series <strong>and</strong> the household's,
     * because on a SHARED tracker anybody's number is the number: a nudge that
     * ignored a reading because somebody else typed it would ask twice for one
     * figure.
     */
    @Query("""
            SELECT DISTINCT r.tracker.id FROM TrackerReading r
            WHERE r.tracker.id IN :trackerIds
            AND r.periodStart = :periodStart
            AND (r.member IS NULL OR r.member.id = :userId)
            """)
    List<UUID> trackersWithReading(@Param("trackerIds") List<UUID> trackerIds,
                                   @Param("periodStart") LocalDate periodStart,
                                   @Param("userId") UUID userId);
}
