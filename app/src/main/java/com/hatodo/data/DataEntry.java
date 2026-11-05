package com.hatodo.data;

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
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One entry in a collection: a name, a description, a detail, and tags
 * (requirements/0006 R51, R52).
 *
 * <p>Two pairs of columns have to agree, and the only durable way to guarantee
 * that is to make disagreement inexpressible. {@code detailHtml} /
 * {@code detailText} and {@code tags} / {@code tagsText} each have exactly one
 * setter, which writes both halves. There is deliberately no
 * {@code setDetailHtml}, no {@code setDetailText}, and no {@code setTagsText} -
 * the second of each pair is what the database's generated
 * {@code search_vector} reads, and a caller able to write one without the other
 * is a search index that quietly stops matching. See adr/0033.
 *
 * <p>Note the asymmetry with {@code TodoItem.done}, which is also a generated
 * column but is mapped read-only because the <em>database</em> computes it from
 * columns Java writes. Here the database computes {@code search_vector} the
 * same way, and {@code detailText} / {@code tagsText} are ordinary columns Java
 * owns. {@code search_vector} itself is not mapped at all: Hibernate has no
 * {@code tsvector} type, and the only thing that reads it is the native search
 * query.
 */
@Entity
@Table(name = "data_entry")
public class DataEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "data_list_id", nullable = false)
    private DataList list;

    @Column(nullable = false)
    private String name;

    @Column
    private String description;

    @Column(name = "detail_html")
    private String detailHtml;

    @Column(name = "detail_text")
    private String detailText;

    /**
     * What R56's filter chips match, with {@code @>} and no stemming.
     *
     * <p>A {@code String[]} rather than a {@code List<String>}: with
     * {@code ddl-auto: validate} a list maps to either an element collection or
     * a serialised blob, and validation says so at startup.
     */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "tags", columnDefinition = "text[]", nullable = false)
    private String[] tags = new String[0];

    /** The same tags as one string, which is what gets stemmed and indexed. */
    @Column(name = "tags_text")
    private String tagsText;

    @Version
    @Column(nullable = false)
    private long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private AppUser createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DataEntry() {
        // for JPA
    }

    public DataEntry(DataList list, String name, AppUser createdBy) {
        this.list = list;
        this.name = name;
        this.createdBy = createdBy;
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

    public DataList getList() {
        return list;
    }

    public UUID getListId() {
        return list.getId();
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

    public String getDetailHtml() {
        return detailHtml;
    }

    public String getDetailText() {
        return detailText;
    }

    /**
     * The only way to write either detail column.
     *
     * @param html the sanitized HTML, exactly as it will be stored
     * @param text the same thing with the markup taken out, for the index
     */
    public void setDetail(String html, String text) {
        this.detailHtml = html;
        this.detailText = text;
    }

    /** A copy: the array is the entity's state, not the caller's to mutate. */
    public List<String> getTags() {
        return tags == null ? List.of() : List.of(tags);
    }

    public String getTagsText() {
        return tagsText;
    }

    /**
     * The only way to write either tag column.
     *
     * @param normalised already lowercased, trimmed, de-duplicated and sorted
     *                   by {@link TagNormaliser} - this does not re-check,
     *                   because one place that knows the rules is the point
     */
    public void setTags(List<String> normalised) {
        this.tags = normalised.toArray(new String[0]);
        this.tagsText = normalised.isEmpty() ? null : String.join(" ", normalised);
    }

    public long getVersion() {
        return version;
    }

    public AppUser getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
