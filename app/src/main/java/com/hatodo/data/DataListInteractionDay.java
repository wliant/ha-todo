package com.hatodo.data;

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
 * How many times one member opened one collection on one household day (0012
 * R100).
 *
 * <p>The only per-member state a collection has. `adr/0029` keeps
 * {@code list_user_state.list_id} pointed at {@code todo_list} and that must
 * not be loosened, so this is a table of its own rather than a widened one -
 * and a collection still has no pin and no recency row (0012 R101 accepts it).
 *
 * <p>**Never written through JPA**: every write goes through
 * {@link DataListInteractionRepository#record}, whose {@code ON CONFLICT ...
 * WHERE} is the fifteen-minute dedup window. A caller that persists one of
 * these directly counts a page refresh as a visit.
 */
@Entity
@Table(name = "data_list_interaction_day")
@IdClass(DataListInteractionDayId.class)
public class DataListInteractionDay {

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "data_list_id", nullable = false)
    private DataList list;

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

    protected DataListInteractionDay() {
        // for JPA
    }

    public DataList getList() {
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
