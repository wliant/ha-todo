package com.hatodo.data;

import com.hatodo.user.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One member's grant on one collection (requirements/0006 R50, reusing R19).
 *
 * <p>No role on the grant, as on a list: everyone who can see a collection is a
 * peer on its entries (R60). Only ownership is special, and that lives on the
 * collection.
 */
@Entity
@Table(name = "data_list_access")
@IdClass(DataListAccessId.class)
public class DataListAccess {

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "data_list_id", nullable = false)
    private DataList list;

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "granted_by")
    private AppUser grantedBy;

    protected DataListAccess() {
        // for JPA
    }

    public DataListAccess(DataList list, AppUser user, AppUser grantedBy) {
        this.list = list;
        this.user = user;
        this.grantedBy = grantedBy;
    }

    @PrePersist
    void onInsert() {
        grantedAt = Instant.now();
    }

    public DataList getList() {
        return list;
    }

    public AppUser getUser() {
        return user;
    }

    public UUID getUserId() {
        return user.getId();
    }

    public Instant getGrantedAt() {
        return grantedAt;
    }
}
