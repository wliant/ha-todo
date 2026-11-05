package com.hatodo.calendar;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A member's own calendar subscriptions (requirements/0007 R62-R66).
 *
 * <p>Under {@code /api/me} because a feed belongs to the caller, so there is no
 * user id in the path to tamper with (adr/0016).
 *
 * <p>Nothing here returns a feed's address. {@link CalendarFeedResponse} has no
 * component for one.
 */
@RestController
@RequestMapping("/api/me/calendars")
public class CalendarFeedController {

    private final CalendarFeedService calendars;
    private final CalendarSyncService sync;

    public CalendarFeedController(CalendarFeedService calendars, CalendarSyncService sync) {
        this.calendars = calendars;
        this.sync = sync;
    }

    @GetMapping
    public List<CalendarFeedResponse> mine() {
        return calendars.mine();
    }

    /** Fetches and shows, saves nothing (R65). */
    @PostMapping("/preview")
    public CalendarPreviewResponse preview(@Valid @RequestBody PreviewCalendarFeedRequest request) {
        return calendars.preview(request.url());
    }

    /**
     * The refresh is a second step rather than part of the transaction that
     * created the feed, so a twenty-second provider does not hold a database
     * connection open - and so a feed that cannot be read is still subscribed,
     * with its error on the screen, rather than silently not created.
     */
    @PostMapping
    public CalendarFeedResponse create(@Valid @RequestBody SaveCalendarFeedRequest request) {
        UUID id = calendars.create(request);
        sync.sync(id);
        return calendars.get(id);
    }

    @PatchMapping("/{id}")
    public CalendarFeedResponse update(@PathVariable UUID id,
                                       @Valid @RequestBody SaveCalendarFeedRequest request) {
        if (calendars.update(id, request)) {
            sync.sync(id);
        }
        return calendars.get(id);
    }

    /**
     * Refresh now.
     *
     * <p>Worth a button because a published {@code .ics} lags its calendar -
     * Google's by hours - so "I just added it, where is it" is the first thing
     * every member will say (adr/0034).
     */
    @PostMapping("/{id}/refresh")
    public CalendarFeedResponse refresh(@PathVariable UUID id) {
        // Checked for ownership first: an id alone must not make this server
        // fetch somebody else's address.
        calendars.get(id);
        sync.sync(id);
        return calendars.get(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        calendars.delete(id);
        return ResponseEntity.noContent().build();
    }
}
