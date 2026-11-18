package com.hatodo.tracker;

import com.hatodo.user.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One member's grant on one tracker (requirements/0019 R164).
 *
 * <p>No role on the grant, as on the other two kinds: everyone who can see a
 * tracker reads every series on it and may record into it. What differs between
 * members is only which series they may <em>correct</em>, and that is decided
 * by the tracker's scope rather than by anything stored here (R165).
 */
@Entity
@Table(name = "tracker_access")
@IdClass(TrackerAccessId.class)
public class TrackerAccess {

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tracker_id", nullable = false)
    private Tracker tracker;

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "granted_by")
    private AppUser grantedBy;

    protected TrackerAccess() {
        // for JPA
    }

    public TrackerAccess(Tracker tracker, AppUser user, AppUser grantedBy) {
        this.tracker = tracker;
        this.user = user;
        this.grantedBy = grantedBy;
    }

    @PrePersist
    void onInsert() {
        grantedAt = Instant.now();
    }

    public Tracker getTracker() {
        return tracker;
    }

    public AppUser getUser() {
        return user;
    }

    public UUID getUserId() {
        return user.getId();
    }

    public Instant getGrantedAt() {
        return grantedAt;
    }
}
