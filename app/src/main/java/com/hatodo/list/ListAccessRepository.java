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
}
