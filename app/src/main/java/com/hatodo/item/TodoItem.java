package com.hatodo.item;

import com.hatodo.list.TodoList;
import com.hatodo.user.AppUser;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One thing to do on one list.
 *
 * <p>Completion and assignment are the writes two members contend on, which is
 * what {@code @Version} is here for: the second writer is told their view was
 * stale rather than silently overwriting the first.
 */
@Entity
@Table(name = "todo_item")
public class TodoItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "list_id", nullable = false)
    private TodoList list;

    @Column(nullable = false)
    private String description;

    @Column(name = "detail_html")
    private String detailHtml;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private AppUser assignee;

    @Column(name = "due_at")
    private Instant dueAt;

    /**
     * Who put the assignee there, so "they claimed it themselves" is a fact
     * about the item rather than about whoever is looking (R47, adr/0026).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_by")
    private AppUser assignedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "completed_by")
    private AppUser completedBy;

    @Column(name = "completed_at")
    private Instant completedAt;

    /** How the work went, written when it was finished (R37). Sanitized on write. */
    @Column(name = "completion_note_html")
    private String completionNoteHtml;

    /** Null while open. Snapshotted at completion, never recomputed. */
    @Column(name = "points_earned")
    private Integer pointsEarned;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reopened_by")
    private AppUser reopenedBy;

    /**
     * Set when the item is reopened; the completion columns are left alone
     * (R49). This is why "finished" is no longer one column - see {@link #done}.
     */
    @Column(name = "reopened_at")
    private Instant reopenedAt;

    /**
     * Computed by the database as {@code completed_at IS NOT NULL AND
     * reopened_at IS NULL}, because a completion that survives a reopen means
     * "has a completion time" no longer means "finished" (adr/0024).
     *
     * <p><strong>Use this only in query predicates and index definitions.</strong>
     * Hibernate does not re-select a generated column after a write, so reading
     * this field in Java straight after {@link #completeBy} sees a stale
     * {@code false} - which would render a just-completed item as open in the
     * very response that completed it. {@link #isCompleted()} is computed
     * instead, and deliberately so.
     */
    @Column(name = "done", insertable = false, updatable = false)
    private boolean done;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private AppUser createdBy;

    /**
     * Which recurrence rule produced this, if any (R42).
     *
     * <p>A plain column rather than a {@code @ManyToOne}, the precedent
     * {@code StoredImage.itemId} sets, so this package does not depend on the
     * recurrence package. The dependency runs one way.
     */
    @Column(name = "rule_id")
    private UUID ruleId;

    /** Which occurrence of that rule. Unique per rule, so a re-run is a no-op. */
    @Column(name = "occurrence_on")
    private LocalDate occurrenceOn;

    @Column(nullable = false)
    private int position;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "item", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    private List<ItemCostValue> costValues = new ArrayList<>();

    protected TodoItem() {
        // for JPA
    }

    public TodoItem(TodoList list, String description, AppUser createdBy, int position) {
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

    public Instant getDueAt() {
        return dueAt;
    }

    public void setDueAt(Instant dueAt) {
        this.dueAt = dueAt;
    }

    public AppUser getAssignedBy() {
        return assignedBy;
    }

    /**
     * @param assignedBy whoever chose the assignee; equal to the assignee when
     *                   they claimed the work themselves
     */
    public void assignTo(AppUser assignee, AppUser assignedBy) {
        this.assignee = assignee;
        this.assignedBy = assignee == null ? null : assignedBy;
    }

    /**
     * True when the assignee put themselves there (R47).
     *
     * <p>Reference identity first, because Hibernate hands out one instance per
     * row within a session and an unsaved entity has no id yet - comparing ids
     * alone would throw on a brand new item and, worse, call two different
     * unsaved members equal because both of their ids are null.
     */
    public boolean isSelfAssigned() {
        if (assignee == null || assignedBy == null) {
            return false;
        }
        return assignee == assignedBy
                || (assignee.getId() != null && assignee.getId().equals(assignedBy.getId()));
    }

    public AppUser getCompletedBy() {
        return completedBy;
    }

    public String getCompletionNoteHtml() {
        return completionNoteHtml;
    }

    public Instant getReopenedAt() {
        return reopenedAt;
    }

    public AppUser getReopenedBy() {
        return reopenedBy;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Integer getPointsEarned() {
        return pointsEarned;
    }

    /**
     * Computed rather than read from {@link #done}, which is stale in memory
     * immediately after a write. Same expression, in Java.
     */
    public boolean isCompleted() {
        return completedAt != null && reopenedAt == null;
    }

    /**
     * @param points what the item was worth at this moment, snapshotted so a
     *               later change to the list's rates cannot rewrite history
     * @param noteHtml how the work went, already sanitized, or null
     */
    public void completeBy(AppUser member, int points, String noteHtml) {
        this.completedBy = member;
        this.completedAt = Instant.now();
        this.pointsEarned = points;
        this.completionNoteHtml = noteHtml;
        // Completing again after a reopen clears the stamp, or the item would
        // still read as reopened and the generated column would stay false.
        this.reopenedAt = null;
        this.reopenedBy = null;
    }

    /**
     * Reopening stamps rather than erases (R49): who completed it, when, and
     * the note are all kept and simply stop being shown. Only the points come
     * back, which is the one thing the requirement does take away.
     */
    public void reopen(AppUser member) {
        this.reopenedBy = member;
        this.reopenedAt = Instant.now();
        this.pointsEarned = null;
    }

    public UUID getRuleId() {
        return ruleId;
    }

    public LocalDate getOccurrenceOn() {
        return occurrenceOn;
    }

    /** Stamped once, when a rule generates this item. */
    public void cameFrom(UUID ruleId, LocalDate occurrenceOn) {
        this.ruleId = ruleId;
        this.occurrenceOn = occurrenceOn;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public long getVersion() {
        return version;
    }

    public List<ItemCostValue> getCostValues() {
        return costValues;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
