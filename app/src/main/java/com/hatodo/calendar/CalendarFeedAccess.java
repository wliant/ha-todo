package com.hatodo.calendar;

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
 * One member's grant on one feed (requirements/0007 R64).
 *
 * <p>Binary, as every grant in this product is: there is nothing to do to an
 * event, so "can read" is the only right there is. The owner holds a row here
 * too, as on a collection, so every membership question is one table.
 */
@Entity
@Table(name = "calendar_feed_access")
@IdClass(CalendarFeedAccessId.class)
public class CalendarFeedAccess {

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "feed_id", nullable = false)
    private CalendarFeed feed;

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt;

    protected CalendarFeedAccess() {
        // for JPA
    }

    public CalendarFeedAccess(CalendarFeed feed, AppUser user) {
        this.feed = feed;
        this.user = user;
    }

    @PrePersist
    void onCreate() {
        grantedAt = Instant.now();
    }

    public CalendarFeed getFeed() {
        return feed;
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
