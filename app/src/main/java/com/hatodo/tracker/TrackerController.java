package com.hatodo.tracker;

import com.hatodo.list.ListIcon;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Trackers (requirements/0019).
 *
 * <p>No security annotations: the chain authenticates every {@code /api} call,
 * and every authorization decision is {@link TrackerService}'s - a tracker id
 * in a URL is never proof of access.
 */
@RestController
@RequestMapping("/api/trackers")
public class TrackerController {

    private final TrackerService trackers;
    private final TrackerReadingService readings;
    private final TrackerSeriesService series;

    public TrackerController(TrackerService trackers, TrackerReadingService readings,
                             TrackerSeriesService series) {
        this.trackers = trackers;
        this.readings = readings;
        this.series = series;
    }

    @GetMapping
    public List<TrackerSummaryResponse> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) ListIcon icon,
            @RequestParam(required = false, defaultValue = "all") String scope) {
        return trackers.search(q, icon, scope);
    }

    /**
     * The sidebar's trackers group (R170).
     *
     * <p>A literal path beside {@code /{id}}: Spring prefers the literal, so
     * "sidebar" is never parsed as a UUID. The same reason "pending" is one.
     */
    @GetMapping("/sidebar")
    public TrackerSidebarResponse sidebar() {
        return trackers.sidebar();
    }

    /**
     * The daily trackers with nothing recorded today (R171).
     *
     * <p>Under {@code /api/trackers} rather than {@code /api/dashboard}, so no
     * dashboard code learns that trackers exist (adr/0029, adr/0051) - the
     * agenda composes its strip from a tracker request of its own.
     *
     * <p>It takes no date. "Today" is the household's and the server's to
     * decide, for adr/0040's reason: a bound a caller supplies is a bound the
     * device's clock can leak into.
     */
    @GetMapping("/pending")
    public List<PendingTrackerResponse> pending() {
        return readings.pending();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TrackerDetailResponse create(@Valid @RequestBody CreateTrackerRequest request) {
        return trackers.create(request);
    }

    @GetMapping("/{id}")
    public TrackerDetailResponse get(@PathVariable UUID id) {
        return trackers.get(id);
    }

    @PatchMapping("/{id}")
    public TrackerDetailResponse update(@PathVariable UUID id,
                                        @Valid @RequestBody UpdateTrackerRequest request) {
        return trackers.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        trackers.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * The chart's data (R167).
     *
     * <p>{@code from} and {@code to} are civil dates, not instants: a bucket is
     * a household date and the server owns that definition, so there is no zone
     * for a client to resolve them against (adr/0052). This is deliberately
     * unlike the activity feed, whose window is instants because only the
     * browser knew where its member's midnight was.
     */
    @GetMapping("/{id}/series")
    public TrackerSeriesResponse series(
            @PathVariable UUID id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate to,
            @RequestParam(required = false) TrackerPeriod by) {
        return series.series(id, from, to, by);
    }

    @GetMapping("/{id}/readings")
    public List<TrackerReadingResponse> readings(@PathVariable UUID id) {
        return readings.list(id);
    }

    /**
     * Records a number (R162).
     *
     * <p>Readable, not managed: recording is the ordinary act a grant has
     * always meant. Which series it lands in is derived from the tracker and
     * the caller, so the body carries no member id.
     */
    @PostMapping("/{id}/readings")
    @ResponseStatus(HttpStatus.CREATED)
    public TrackerReadingResponse record(@PathVariable UUID id,
                                         @Valid @RequestBody SaveReadingRequest request) {
        return readings.record(id, request);
    }

    @GetMapping("/{id}/access")
    public List<TrackerMemberResponse> members(@PathVariable UUID id) {
        return trackers.members(id);
    }

    @PutMapping("/{id}/access")
    public List<TrackerMemberResponse> replaceMembers(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTrackerAccessRequest request) {
        return trackers.replaceMembers(id, request);
    }

    /** PUT and DELETE rather than a body, as on the other two kinds: the pin is the resource. */
    @PutMapping("/{id}/pin")
    public ResponseEntity<Void> pin(@PathVariable UUID id) {
        trackers.pin(id, true);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/pin")
    public ResponseEntity<Void> unpin(@PathVariable UUID id) {
        trackers.pin(id, false);
        return ResponseEntity.noContent().build();
    }

    /** "I opened this." A POST, so a read stays a read and a prefetch reorders nothing. */
    @PostMapping("/{id}/touch")
    public ResponseEntity<Void> touch(@PathVariable UUID id) {
        trackers.touch(id);
        return ResponseEntity.noContent().build();
    }
}
