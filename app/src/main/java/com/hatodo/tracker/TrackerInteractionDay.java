package com.hatodo.tracker;

import com.hatodo.user.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

/**
 * How many times one member opened one tracker on one household day (0012
 * R100, applied to the third kind).
 *
 * <p><strong>Never written through JPA</strong>: every write goes through
 * {@link TrackerInteractionRepository#record}, whose {@code ON CONFLICT ...
 * WHERE} clause is the fifteen-minute dedup window. A caller that persists one
 * of these directly counts a page refresh as a visit.
 *
 * <p>{@code onDay} is the household's day, supplied by the application, for the
 * reason {@code period_start} is (adr/0036, adr/0041).
 */
@Entity
@Table(name = "tracker_interaction_day")
@IdClass(TrackerInteractionDayId.class)
public class TrackerInteractionDay {

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tracker_id", nullable = false)
    private Tracker tracker;

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Id
    @Column(name = "on_day", nullable = false)
    private LocalDate onDay;

    @Column(nullable = false)
    private int hits;

    @Column(name = "last_at", nullable = false)
    private Instant lastAt;

    protected TrackerInteractionDay() {
        // for JPA
    }

    public Tracker getTracker() {
        return tracker;
    }

    public LocalDate getOnDay() {
        return onDay;
    }

    public int getHits() {
        return hits;
    }

    public Instant getLastAt() {
        return lastAt;
    }
}
