package com.hatodo.list;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ListFieldRepository extends JpaRepository<ListField, UUID> {

    @Query("SELECT f FROM ListField f WHERE f.list.id = :listId ORDER BY f.position ASC")
    List<ListField> findForList(@Param("listId") UUID listId);
}
