package com.hatodo.calendar;

import com.hatodo.user.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

/**
 * A calendar somebody else publishes, subscribed by one member
 * (requirements/0007 R62).
 *
 * <p>Read-only by construction: this product never creates, edits or deletes an
 * event, so there is nothing here but an address, a label, who may see it and
 * how the last refresh went.
 *
 * <p>The address is the dangerous part. It authenticates nobody - whoever holds
 * the string reads that calendar indefinitely - so it is written and replaced
 * and never read back out of the application. {@code CalendarFeedResponse} has
 * no component for it, which is a stronger guarantee than remembering not to
 * populate one. See docs/adr/0035.
 */
@Entity
@Table(name = "calendar_feed")
public class CalendarFeed {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Whoever may change the feed and decide who sees it. Not transferable. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private AppUser owner;

    @Column(nullable = false)
    private String label;

    @Column(nullable = false)
    private String url;

    @Column(name = "url_digest", nullable = false, length = 64)
    private String urlDigest;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CalendarProvider provider = CalendarProvider.OTHER;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    @Column(name = "last_success_at")
    private Instant lastSuccessAt;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "consecutive_errors", nullable = false)
    private short consecutiveErrors;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CalendarFeed() {
        // for JPA
    }

    public CalendarFeed(AppUser owner, String label, URI url) {
        this.owner = owner;
        this.label = label;
        setUrl(url);
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    /**
     * Replaces the address, its digest and the inferred provider together.
     *
     * <p>One setter for the three, for the reason adr/0033 gives about an
     * entry's search document: a caller able to write one of them is a feed
     * whose digest no longer matches its URL, and the duplicate check silently
     * stops working.
     */
    public void setUrl(URI url) {
        this.url = url.toString();
        this.urlDigest = digest(this.url);
        this.provider = CalendarProvider.of(url);
    }

    public static String digest(String url) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(url.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required of every JVM", impossible);
        }
    }

    public void recordAttempt(Instant at) {
        this.lastAttemptAt = at;
    }

    public void recordSuccess(Instant at) {
        this.lastAttemptAt = at;
        this.lastSuccessAt = at;
        this.lastError = null;
        this.consecutiveErrors = 0;
    }

    /**
     * @param reason a short phrase the owner reads on a screen - a status, "not
     *               a calendar", "too large". Never an exception message, never
     *               the URL, and never anything echoed from the response body.
     */
    public void recordFailure(Instant at, String reason) {
        this.lastAttemptAt = at;
        this.lastError = reason == null || reason.length() <= 300
                ? reason
                : reason.substring(0, 300);
        this.consecutiveErrors = (short) Math.min(this.consecutiveErrors + 1, Short.MAX_VALUE);
    }

    public UUID getId() {
        return id;
    }

    public AppUser getOwner() {
        return owner;
    }

    public UUID getOwnerId() {
        return owner.getId();
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    /**
     * For the fetcher alone.
     *
     * <p>Nothing that builds a response may call this, and nothing does -
     * {@code CalendarFeedResponse} has no field it could be put in.
     */
    public URI getUrl() {
        return URI.create(url);
    }

    public String getUrlDigest() {
        return urlDigest;
    }

    public CalendarProvider getProvider() {
        return provider;
    }

    public Instant getLastAttemptAt() {
        return lastAttemptAt;
    }

    public Instant getLastSuccessAt() {
        return lastSuccessAt;
    }

    public String getLastError() {
        return lastError;
    }

    public short getConsecutiveErrors() {
        return consecutiveErrors;
    }
}
