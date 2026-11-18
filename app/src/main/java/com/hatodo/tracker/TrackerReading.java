package com.hatodo.tracker;

import com.hatodo.user.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One number, in one bucket, in one series (requirements/0019 R162).
 *
 * <p>{@code member} is the series this reading belongs to, and <strong>null is
 * the household's</strong> - a SHARED tracker's readings all carry null, with
 * no companion boolean, for the reason {@code password_hash} has none
 * (adr/0052). {@code recordedBy} is authorship, which is a different question:
 * on a shared tracker it is the only record of who typed a number, and it
 * survives the member's deletion while the series reference cascades (R172).
 *
 * <p>{@code periodStart} is always the canonical start of its bucket, in the
 * household's zone. The service canonicalises it on the way in, because the
 * tracker's period lives in another table and no constraint here can see it.
 */
@Entity
@Table(name = "tracker_reading")
public class TrackerReading {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tracker_id", nullable = false)
    private Tracker tracker;

    /** Whose series, or null for the household's. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private AppUser member;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    /** Order within a bucket: what LATEST reads, and what an edit keeps. */
    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column
    private String note;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recorded_by")
    private AppUser recordedBy;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TrackerReading() {
        // for JPA
    }

    public TrackerReading(
            Tracker tracker,
            AppUser member,
            LocalDate periodStart,
            BigDecimal amount,
            String note,
            AppUser recordedBy) {
        this.tracker = tracker;
        this.member = member;
        this.periodStart = periodStart;
        this.amount = amount;
        this.note = note;
        this.recordedBy = recordedBy;
        this.recordedAt = Instant.now();
    }

    @PrePersist
    void onInsert() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (recordedAt == null) {
            recordedAt = now;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    /**
     * Whether this member may correct this number (R165).
     *
     * <p>One predicate, written once, because two callers need the same answer:
     * the service, which refuses, and the response, which publishes it as
     * {@code editable} for <em>this</em> caller. A second copy is how a member
     * gets an edit control that 403s - or, worse and failing nowhere, no
     * control on a number they are entitled to fix. The same rule
     * {@code RecurrenceRule.isWritableBy} follows (adr/0049).
     *
     * <p>On a SHARED tracker the series is the household's, so anyone with
     * access may correct it. On a PER_MEMBER tracker a member corrects only
     * their own. Readability is a separate and earlier question: a reading on a
     * tracker the caller holds no grant on is a 404, not a 403.
     */
    public boolean isWritableBy(UUID userId) {
        return tracker.getScope() == TrackerScope.SHARED
                || (member != null && member.getId().equals(userId));
    }

    public UUID getId() {
        return id;
    }

    public Tracker getTracker() {
        return tracker;
    }

    public UUID getTrackerId() {
        return tracker.getId();
    }

    public AppUser getMember() {
        return member;
    }

    /** Null for the household's series. */
    public UUID getMemberId() {
        return member == null ? null : member.getId();
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public void setPeriodStart(LocalDate periodStart) {
        this.periodStart = periodStart;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public AppUser getRecordedBy() {
        return recordedBy;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
