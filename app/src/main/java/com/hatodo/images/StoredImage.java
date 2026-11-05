package com.hatodo.images;

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
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * The inventory row for one object in the bucket.
 *
 * <p>Images are never mutated - replacing one is a new row and a new id - which
 * is what lets the API serve them with a strong ETag and a long private cache.
 */
@Entity
@Table(name = "stored_image")
public class StoredImage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "object_key", nullable = false, unique = true)
    private String objectKey;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private int sizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ImagePurpose purpose;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by")
    private AppUser uploadedBy;

    /**
     * The item whose detail field uses this picture, and therefore whose list
     * decides who may read it. Null while unclaimed - uploaded into an item
     * that was never saved.
     */
    @Column(name = "item_id")
    private UUID itemId;

    /**
     * The recurrence rule that owns this picture, if a rule does.
     *
     * <p>Exclusive with {@code itemId}, which the database also insists on: a
     * picture belongs to an item or to a rule, never both.
     */
    @Column(name = "rule_id")
    private UUID ruleId;

    /**
     * The collection entry that owns this picture, if an entry does
     * (requirements/0006 R61).
     *
     * <p>A third nullable owner beside {@code itemId} and {@code ruleId}, which
     * the database constrains with {@code num_nonnulls(...) <= 1}. The shape
     * does not scale forever: adr/0032 says a fourth owner is the point to
     * replace it with a join table rather than add a fifth column.
     */
    @Column(name = "entry_id")
    private UUID entryId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected StoredImage() {
        // for JPA
    }

    public StoredImage(String objectKey, String contentType, int sizeBytes,
                       ImagePurpose purpose, AppUser uploadedBy) {
        this.objectKey = objectKey;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.purpose = purpose;
        this.uploadedBy = uploadedBy;
    }

    @PrePersist
    void onInsert() {
        createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public String getContentType() {
        return contentType;
    }

    public int getSizeBytes() {
        return sizeBytes;
    }

    public ImagePurpose getPurpose() {
        return purpose;
    }

    public AppUser getUploadedBy() {
        return uploadedBy;
    }

    public UUID getRuleId() {
        return ruleId;
    }

    /** Claimed by a rule, so from now on the rule's list decides who may read it. */
    public void claimForRule(UUID ruleId) {
        this.ruleId = ruleId;
    }

    public UUID getItemId() {
        return itemId;
    }

    public void claimFor(UUID itemId) {
        this.itemId = itemId;
    }

    public UUID getEntryId() {
        return entryId;
    }

    /**
     * Claimed by a collection entry, so from now on that entry's collection
     * decides who may read it.
     */
    public void claimForEntry(UUID entryId) {
        this.entryId = entryId;
    }

    /** No owner at all: uploaded into something that was never saved. */
    public boolean isUnclaimed() {
        return itemId == null && ruleId == null && entryId == null;
    }

    public boolean wasUploadedBy(UUID userId) {
        return uploadedBy != null && uploadedBy.getId().equals(userId);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
