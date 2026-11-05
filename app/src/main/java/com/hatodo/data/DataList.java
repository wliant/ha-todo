package com.hatodo.data;

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
import java.time.Instant;
import java.util.UUID;

/**
 * A reference collection - recipes, restaurants, quotes, vocabulary
 * (requirements/0006 R50).
 *
 * <p>A sibling of {@code TodoList}, not a kind of one. The two carry almost the
 * same columns and that is deliberate: a discriminator on {@code todo_list}
 * would make nine existing reads of that table wrong by omission, and
 * {@code TodoListService.create} would hand a collection the due-date field
 * 0006 says cannot exist. See adr/0029.
 *
 * <p>What is <em>not</em> here is the point: no assignment, no due date, no
 * completion, no points, no recurrence. A recipe is not work.
 */
@Entity
@Table(name = "data_list")
public class DataList {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Whoever may manage the collection. Not transferable, as on a list. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private AppUser owner;

    @Column(nullable = false)
    private String name;

    @Column
    private String description;

    /**
     * The same curated set a list draws from, shared rather than copied: it is
     * a closed set of drawings with no behaviour, and the frontend mirror is
     * one file.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ListIcon icon = ListIcon.CHECKLIST;

    /**
     * The settings screen replaces the whole access list, so two tabs editing
     * one collection is the case to be told about rather than silently lose.
     */
    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DataList() {
        // for JPA
    }

    public DataList(AppUser owner, String name, String description, ListIcon icon) {
        this.owner = owner;
        this.name = name;
        this.description = description;
        this.icon = icon;
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
