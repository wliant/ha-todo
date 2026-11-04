package com.hatodo.list;

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
 * One member's grant on one list (R19).
 *
 * <p>There is deliberately no role on the grant: everyone who can see a list is
 * a peer on it, which is requirements/0001's premise unchanged. Only ownership
 * is special, and that lives on the list.
 */
@Entity
@Table(name = "list_access")
@IdClass(ListAccessId.class)
public class ListAccess {

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "list_id", nullable = false)
    private TodoList list;

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "granted_by")
    private AppUser grantedBy;

    protected ListAccess() {
        // for JPA
    }

    public ListAccess(TodoList list, AppUser user, AppUser grantedBy) {
        this.list = list;
        this.user = user;
        this.grantedBy = grantedBy;
    }

    @PrePersist
    void onInsert() {
        grantedAt = Instant.now();
    }

    public TodoList getList() {
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
