package com.hatodo.data;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DataEntryRepository extends JpaRepository<DataEntry, UUID> {

    /** One collection's entries in the order R57 displays them. */
    @Query("SELECT e FROM DataEntry e WHERE e.list.id = :listId ORDER BY LOWER(e.name) ASC")
    List<DataEntry> findForList(@Param("listId") UUID listId);

    @Query("SELECT COUNT(e) FROM DataEntry e WHERE e.list.id = :listId")
    long countForList(@Param("listId") UUID listId);

    /**
     * An entry together with the collection it is on, so the service can ask
     * one question of the access rule rather than lazy-load on the way.
     */
    @Query("SELECT e FROM DataEntry e JOIN FETCH e.list WHERE e.id = :id")
    Optional<DataEntry> findWithList(@Param("id") UUID id);
}
