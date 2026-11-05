package com.hatodo.data;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DataListAccessRepository
        extends JpaRepository<DataListAccess, DataListAccessId> {

    @Query("""
            SELECT a FROM DataListAccess a
            WHERE a.list.id = :listId ORDER BY LOWER(a.user.displayName)
            """)
    List<DataListAccess> findForList(@Param("listId") UUID listId);

    @Query("SELECT a FROM DataListAccess a WHERE a.list.id = :listId AND a.user.id = :userId")
    Optional<DataListAccess> find(@Param("listId") UUID listId, @Param("userId") UUID userId);

    @Query("""
            SELECT COUNT(a) > 0 FROM DataListAccess a
            WHERE a.list.id = :listId AND a.user.id = :userId
            """)
    boolean exists(@Param("listId") UUID listId, @Param("userId") UUID userId);
}
