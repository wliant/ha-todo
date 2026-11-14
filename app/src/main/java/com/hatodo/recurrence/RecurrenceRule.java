package com.hatodo.recurrence;

import com.hatodo.list.TodoList;
import com.hatodo.user.AppUser;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A standing instruction on a list (R41): a template, plus when it recurs.
 *
 * <p>Managed apart from the items it produces, because a rule is not an item -
 * editing next week's bins should not mean editing this week's. The items it
 * has already made are ordinary items and outlive it.
 */
@Entity
@Table(name = "recurrence_rule")
public class RecurrenceRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "list_id", nullable = false)
    private TodoList list;

    @Column(nullable = false)
    private String description;

    /** Sanitized server-side on write, exactly as an item's detail is. */
    @Column(name = "detail_html")
    private String detailHtml;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private AppUser assignee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_by")
    private AppUser assignedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Frequency frequency;

    @Column(name = "interval_count", nullable = false)
    private short intervalCount = 1;

    /** WEEKLY only. See {@link Weekdays} for the bit mapping. */
    @Column(name = "days_of_week")
    private Short daysOfWeek;

    /** MONTHLY only. Clamped to the month's length when it is generated. */
    @Column(name = "day_of_month")
    private Short dayOfMonth;

    /** A wall clock in the household's zone, or null for an undated item. */
    @Column(name = "due_time")
    private LocalTime dueTime;

    @Column(name = "lead_days", nullable = false)
    private short leadDays;

    @Column(name = "starts_on", nullable = false)
    private LocalDate startsOn;

    @Column(name = "ends_on")
    private LocalDate endsOn;

    @Column(nullable = false)
    private boolean paused;

    /** Generation runs strictly after this, which is the no-op guard. */
    @Column(name = "last_materialised_on")
    private LocalDate lastMaterialisedOn;

    @Column(name = "last_run_at")
    private Instant lastRunAt;

    /** Why the last run produced nothing, so the tab can say so. */
    @Column(name = "last_error")
    private String lastError;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private AppUser createdBy;

    @Column(nullable = false)
    private short position;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "rule", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    private List<RecurrenceCostValue> costValues = new ArrayList<>();

    protected RecurrenceRule() {
        // for JPA
    }

    public RecurrenceRule(TodoList list, String description, AppUser createdBy, short position) {
        this.list = list;
        this.description = description;
        this.createdBy = createdBy;
        this.position = position;
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

    public UUID getId() {
        return id;
    }

    public TodoList getList() {
        return list;
    }

    public UUID getListId() {
        return list.getId();
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getDetailHtml() {
        return detailHtml;
    }

    public void setDetailHtml(String detailHtml) {
        this.detailHtml = detailHtml;
    }

    public AppUser getAssignee() {
        return assignee;
    }

    public AppUser getAssignedBy() {
        return assignedBy;
    }

    /** Mirrors the item's own rule: the two columns move together. */
    public void assignTo(AppUser assignee, AppUser assignedBy) {
        this.assignee = assignee;
        this.assignedBy = assignee == null ? null : assignedBy;
    }

    public Frequency getFrequency() {
        return frequency;
    }

    public int getIntervalCount() {
        return intervalCount;
    }

    public int getDaysOfWeek() {
        return daysOfWeek == null ? Weekdays.NONE : daysOfWeek;
    }

    public int getDayOfMonth() {
        return dayOfMonth == null ? 1 : dayOfMonth;
    }

    /**
     * The schedule as one move, because the three parameters are only valid in
     * the combinations the database also insists on.
     */
    public void schedule(Frequency frequency, int intervalCount, Integer daysOfWeek,
                         Integer dayOfMonth) {
        this.frequency = frequency;
        this.intervalCount = (short) intervalCount;
        this.daysOfWeek = frequency == Frequency.WEEKLY ? (short) (int) daysOfWeek : null;
        this.dayOfMonth = frequency == Frequency.MONTHLY ? (short) (int) dayOfMonth : null;
    }

    public LocalTime getDueTime() {
        return dueTime;
    }

    public void setDueTime(LocalTime dueTime) {
        this.dueTime = dueTime;
    }

    public int getLeadDays() {
        return leadDays;
    }

    public void setLeadDays(int leadDays) {
        this.leadDays = (short) leadDays;
    }

    public LocalDate getStartsOn() {
        return startsOn;
    }

    public void setStartsOn(LocalDate startsOn) {
        this.startsOn = startsOn;
    }

    public LocalDate getEndsOn() {
        return endsOn;
    }

    public void setEndsOn(LocalDate endsOn) {
        this.endsOn = endsOn;
    }

    public boolean isPaused() {
        return paused;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    public LocalDate getLastMaterialisedOn() {
        return lastMaterialisedOn;
    }

    public Instant getLastRunAt() {
        return lastRunAt;
    }

    public String getLastError() {
        return lastError;
    }

    /** Records what a run did, whether or not it produced anything. */
    public void recordRun(LocalDate materialisedThrough, String error) {
        this.lastRunAt = Instant.now();
        this.lastError = error;
        if (materialisedThrough != null
                && (lastMaterialisedOn == null || materialisedThrough.isAfter(lastMaterialisedOn))) {
            this.lastMaterialisedOn = materialisedThrough;
        }
    }

    public AppUser getCreatedBy() {
        return createdBy;
    }

    /**
     * Whoever wrote it, or whoever owns the list it is on (0016 R143).
     *
     * <p>Here rather than in the service because two callers need the same
     * answer and must not each spell it out: the write path, which refuses, and
     * {@link RecurrenceRuleResponse}, which tells the client whether to draw
     * the edit control. Two copies of this is how a member gets a button that
     * 403s - or, worse because nothing fails, no button on a rule they are
     * entitled to edit (adr/0049).
     *
     * <p>The null branch is real rather than defensive: {@code created_by} is
     * {@code ON DELETE SET NULL}, so an authorless rule is the owner's alone.
     */
    public boolean isWritableBy(UUID userId) {
        return list.isOwnedBy(userId)
                || (createdBy != null && createdBy.getId().equals(userId));
    }

    public short getPosition() {
        return position;
    }

    public void setPosition(short position) {
        this.position = position;
    }

    public long getVersion() {
        return version;
    }

    public List<RecurrenceCostValue> getCostValues() {
        return costValues;
    }
}
