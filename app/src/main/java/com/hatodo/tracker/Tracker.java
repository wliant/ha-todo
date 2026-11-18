package com.hatodo.tracker;

import com.hatodo.list.ListIcon;
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
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * A number a household records over and over (requirements/0019 R159-R161).
 *
 * <p>A sibling of {@code TodoList} and {@code DataList}, not a kind of either.
 * What is <em>not</em> here is again the point: no assignment, no due date, no
 * completion, no points, no recurrence, and no searchable document. A weight is
 * neither work nor reference. See adr/0051.
 *
 * <p>The declaration - unit, period, scope, rollup - is what every other part
 * of the feature reads: the form asks for the period's bucket, the series folds
 * by the rollup, and the chart picks its shape from the rollup. The unit is a
 * label and never a conversion (R159): two units are two trackers.
 */
@Entity
@Table(name = "tracker")
public class Tracker {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Whoever may manage the tracker. Not transferable, as on the other two. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private AppUser owner;

    @Column(nullable = false)
    private String name;

    @Column
    private String description;

    /** The curated set all three kinds draw from (adr/0029). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ListIcon icon = ListIcon.CHECKLIST;

    @Column(nullable = false)
    private String unit;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TrackerPeriod period;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TrackerScope scope;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Rollup rollup;

    @Column(nullable = false)
    private short decimals = 1;

    @Column
    private BigDecimal target;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_direction")
    private TargetDirection targetDirection;

    /** The sharing screen is the contended write, as on the other two kinds. */
    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Tracker() {
        // for JPA
    }

    public Tracker(
            AppUser owner,
            String name,
            String description,
            ListIcon icon,
            String unit,
            TrackerPeriod period,
            TrackerScope scope,
            Rollup rollup) {
        this.owner = owner;
        this.name = name;
        this.description = description;
        this.icon = icon;
        this.unit = unit;
        this.period = period;
        this.scope = scope;
        this.rollup = rollup;
    }

    @PrePersist
    void onInsert() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    /**
     * The series this member writes into, empty meaning the household's.
     *
     * <p>Derived from the scope and the caller, never taken from a request:
     * there is no {@code memberId} parameter anywhere in this feature's write
     * path, so there is none to tamper with. The same reasoning as
     * {@code /api/me/*} carrying no user id (adr/0016), one level down.
     */
    public Optional<UUID> seriesFor(UUID userId) {
        return scope == TrackerScope.SHARED ? Optional.empty() : Optional.of(userId);
    }

    /**
     * Both halves of a target, or neither.
     *
     * <p>Mirrors the table's check rather than trusting it: a figure with no
     * direction cannot be read, so it is refused where it is set.
     */
    public void setTarget(BigDecimal target, TargetDirection direction) {
        if ((target == null) != (direction == null)) {
            throw new IllegalArgumentException("A target needs a direction, and a direction a target");
        }
        this.target = target;
        this.targetDirection = direction;
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

    public boolean isOwnedBy(UUID userId) {
        return owner.getId().equals(userId);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public ListIcon getIcon() {
        return icon;
    }

    public void setIcon(ListIcon icon) {
        this.icon = icon;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public TrackerPeriod getPeriod() {
        return period;
    }

    /**
     * Only before the first reading - enforced by {@code TrackerService}, which
     * is the only thing able to ask whether one exists (R161).
     */
    public void setPeriod(TrackerPeriod period) {
        this.period = period;
    }

    public TrackerScope getScope() {
        return scope;
    }

    /** Only before the first reading, for the reason {@link #setPeriod} is. */
    public void setScope(TrackerScope scope) {
        this.scope = scope;
    }

    public Rollup getRollup() {
        return rollup;
    }

    public void setRollup(Rollup rollup) {
        this.rollup = rollup;
    }

    public short getDecimals() {
        return decimals;
    }

    public void setDecimals(short decimals) {
        this.decimals = decimals;
    }

    public BigDecimal getTarget() {
        return target;
    }

    public TargetDirection getTargetDirection() {
        return targetDirection;
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
