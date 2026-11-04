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

/**
 * What one member has done with one list: pinned it (R27), and when they last
 * touched it (R28). Both are personal - one member's pins have no effect on
 * another's.
 *
 * <p>No {@code @Version}: last write wins is the right semantics for a pin
 * toggle and a timestamp, and a conflict on either would be noise.
 */
@Entity
@Table(name = "list_user_state")
@IdClass(ListUserStateId.class)
public class ListUserState {

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "list_id", nullable = false)
    private TodoList list;

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    /** Null *is* "not pinned". There is no companion boolean to contradict it. */
    @Column(name = "pinned_at")
    private Instant pinnedAt;

    @Column(name = "last_interacted_at")
    private Instant lastInteractedAt;

    protected ListUserState() {
        // for JPA
    }

    public ListUserState(TodoList list, AppUser user) {
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

    public Instant getLastInteractedAt() {
        return lastInteractedAt;
    }

    public void touch() {
        lastInteractedAt = Instant.now();
    }
}
