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

/**
 * What one member has done with one collection: pinned it (0012 R104).
 *
 * <p>A table of its own rather than a row on {@code list_user_state}, whose
 * {@code list_id} is an FK to {@code todo_list} and must not be loosened - see
 * adr/0029 for the separation and adr/0043 for why a collection may now be
 * pinned despite it.
 *
 * <p>Deliberately thinner than {@link com.hatodo.list.ListUserState}: no
 * {@code lastInteractedAt}, because a collection's recency comes from its
 * interaction buckets (adr/0041) and a second column holding it is a second
 * thing to keep true.
 *
 * <p>No {@code @Version}: last write wins is the right semantics for a pin
 * toggle, and a conflict on one would be noise.
 */
@Entity
@Table(name = "data_list_user_state")
@IdClass(DataListUserStateId.class)
public class DataListUserState {

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "data_list_id", nullable = false)
    private DataList list;

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    /** Null *is* "not pinned". There is no companion boolean to contradict it. */
    @Column(name = "pinned_at")
    private Instant pinnedAt;

    protected DataListUserState() {
        // for JPA
    }

    public DataListUserState(DataList list, AppUser user) {
        this.list = list;
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
