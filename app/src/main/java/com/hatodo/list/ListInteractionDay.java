package com.hatodo.list;

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
 * How many times one member opened one list on one household day (0012 R100).
 *
 * <p>Mapped so the buckets can be read - by a test, and by anybody wondering
 * why a list sits where it does in the sidebar. **Never written through JPA**:
 * every write goes through {@link ListInteractionRepository#record}, whose
 * {@code ON CONFLICT ... WHERE} is where the fifteen-minute dedup window lives.
 * A caller that persists one of these directly counts a page refresh as a
 * visit, and the sidebar's ordering quietly starts measuring reloads.
 *
 * <p>No {@code @Version}: the only write is an atomic upsert.
 */
@Entity
@Table(name = "list_interaction_day")
@IdClass(ListInteractionDayId.class)
public class ListInteractionDay {

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "list_id", nullable = false)
    private TodoList list;

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    /** The household's day, not UTC's and not the device's (adr/0036). */
    @Id
    @Column(name = "on_day", nullable = false)
    private LocalDate onDay;

    @Column(nullable = false)
    private int hits;

    @Column(name = "last_at", nullable = false)
    private Instant lastAt;

    protected ListInteractionDay() {
        // for JPA
    }

    public TodoList getList() {
        return list;
    }

    public AppUser getUser() {
        return user;
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
