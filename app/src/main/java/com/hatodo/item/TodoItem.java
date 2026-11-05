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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "completed_by")
    private AppUser completedBy;

    @Column(name = "completed_at")
    private Instant completedAt;

    /** Null while open. Snapshotted at completion, never recomputed. */
    @Column(name = "points_earned")
    private Integer pointsEarned;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private AppUser createdBy;

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

    public void setAssignee(AppUser assignee) {
        this.assignee = assignee;
    }

    public Instant getDueAt() {
        return dueAt;
    }

    public void setDueAt(Instant dueAt) {
        this.dueAt = dueAt;
    }

    public AppUser getCompletedBy() {
        return completedBy;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Integer getPointsEarned() {
        return pointsEarned;
    }

    public boolean isCompleted() {
        return completedAt != null;
    }

    /**
     * @param points what the item was worth at this moment, snapshotted so a
     *               later change to the list's rates cannot rewrite history
     */
    public void completeBy(AppUser member, int points) {
        this.completedBy = member;
        this.completedAt = Instant.now();
        this.pointsEarned = points;
    }

    /** The three completion columns move together, as the database insists. */
    public void reopen() {
        this.completedBy = null;
        this.completedAt = null;
        this.pointsEarned = null;
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
