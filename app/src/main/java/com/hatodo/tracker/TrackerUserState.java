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

/**
 * What one member has done with one tracker: pinned it (requirements/0019
 * R170).
 *
 * <p>A table of its own for the reason {@code data_list_user_state} is one:
 * {@code list_user_state.list_id} is an FK to {@code todo_list} and does not
 * get loosened (adr/0029, adr/0043). The third copy of that decision.
 *
 * <p>As thin as the collections one: a pin and nothing else, because a
 * tracker's recency comes from its interaction buckets (adr/0041).
 *
 * <p>No {@code @Version}: last write wins is the right semantics for a toggle.
 */
@Entity
@Table(name = "tracker_user_state")
@IdClass(TrackerUserStateId.class)
public class TrackerUserState {

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tracker_id", nullable = false)
    private Tracker tracker;

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    /** Null *is* "not pinned". There is no companion boolean to contradict it. */
    @Column(name = "pinned_at")
    private Instant pinnedAt;

    protected TrackerUserState() {
        // for JPA
    }

    public TrackerUserState(Tracker tracker, AppUser user) {
        this.tracker = tracker;
        this.user = user;
    }

    public boolean isPinned() {
        return pinnedAt != null;
    }

    public Instant getPinnedAt() {
        return pinnedAt;
    }

    public void pin() {
        if (pinnedAt == null) {
            pinnedAt = Instant.now();
        }
    }

    public void unpin() {
        pinnedAt = null;
    }
}
