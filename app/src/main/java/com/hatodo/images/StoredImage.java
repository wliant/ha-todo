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

    public Instant getCreatedAt() {
        return createdAt;
    }
}
