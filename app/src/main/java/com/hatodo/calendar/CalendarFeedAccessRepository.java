package com.hatodo.calendar;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CalendarFeedAccessRepository
        extends JpaRepository<CalendarFeedAccess, CalendarFeedAccessId> {

    @Query("SELECT a FROM CalendarFeedAccess a WHERE a.feed.id = :feedId")
    List<CalendarFeedAccess> findForFeed(@Param("feedId") UUID feedId);
}
