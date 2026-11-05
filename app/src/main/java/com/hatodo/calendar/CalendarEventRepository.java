package com.hatodo.calendar;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CalendarEventRepository extends JpaRepository<CalendarEvent, UUID> {

    /**
     * Everything a member may see in a window, newest feed state only.
     *
     * <p>The grant join is the authorization, in SQL. A predicate applied after
     * fetching passes every unit test and publishes every member's calendar -
     * the same argument the list repositories make, with a sharper edge here
     * because the rows are somebody's appointments.
     *
     * <p>The feed and its owner are fetched along with the row because every
     * event is rendered with whose calendar it came from, and the alternative
     * is a query per event.
     *
     * @param mineOnly the dashboard's {@code scope=mine}, which for an event
     *                 means "from a calendar I own" - an event is never
     *                 assigned, so there is nothing else it could mean
     */
    @Query("""
            SELECT e FROM CalendarEvent e
            JOIN FETCH e.feed f
            JOIN FETCH f.owner
            WHERE e.occursOn >= :from AND e.occursOn <= :to
            AND EXISTS (
                SELECT 1 FROM CalendarFeedAccess a
                WHERE a.feed = f AND a.user.id = :userId
            )
            AND (:mineOnly = FALSE OR f.owner.id = :userId)
            ORDER BY e.occursOn ASC, e.sortMinute ASC, LOWER(e.summary) ASC
            """)
    List<CalendarEvent> findVisibleBetween(@Param("userId") UUID userId,
                                           @Param("from") LocalDate from,
                                           @Param("to") LocalDate to,
                                           @Param("mineOnly") boolean mineOnly,
                                           Pageable pageable);

    @Modifying
    @Query("DELETE FROM CalendarEvent e WHERE e.feed.id = :feedId")
    void deleteForFeed(@Param("feedId") UUID feedId);

    @Query("SELECT COUNT(e) FROM CalendarEvent e WHERE e.feed.id = :feedId")
    long countForFeed(@Param("feedId") UUID feedId);
}
