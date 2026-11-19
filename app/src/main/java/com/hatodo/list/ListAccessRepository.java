package com.hatodo.list;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ListAccessRepository extends JpaRepository<ListAccess, ListAccessId> {

    @Query("SELECT a FROM ListAccess a WHERE a.list.id = :listId ORDER BY LOWER(a.user.displayName)")
    List<ListAccess> findForList(@Param("listId") UUID listId);

    @Query("SELECT a FROM ListAccess a WHERE a.list.id = :listId AND a.user.id = :userId")
    Optional<ListAccess> find(@Param("listId") UUID listId, @Param("userId") UUID userId);

    @Query("SELECT COUNT(a) > 0 FROM ListAccess a WHERE a.list.id = :listId AND a.user.id = :userId")
    boolean exists(@Param("listId") UUID listId, @Param("userId") UUID userId);

    /**
     * Everybody who may see this list, as ids alone.
     *
     * <p><strong>{@code ListAccess} alone, deliberately - the owner is not
     * unioned in.</strong> {@code TodoListService.create} writes the owner
     * their own grant row, and this has to stay the <em>same predicate</em> as
     * the reads it is used to trigger: the dashboard's guard is
     * {@code EXISTS (SELECT 1 FROM ListAccess a WHERE a.list = i.list AND
     * a.user.id = :userId)}. A broader set here would notify somebody about a
     * chore their agenda then does not show; a narrower one would leave
     * somebody stale. See docs/adr/0055.
     */
    @Query("SELECT a.user.id FROM ListAccess a WHERE a.list.id = :listId")
    List<UUID> findUserIdsForList(@Param("listId") UUID listId);
}
