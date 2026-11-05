package com.hatodo.images;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoredImageRepository extends JpaRepository<StoredImage, UUID> {

    @Query("SELECT i FROM StoredImage i WHERE i.itemId = :itemId")
    List<StoredImage> findForItem(@Param("itemId") UUID itemId);

    /**
     * Every picture on every item of one list.
     *
     * <p>Written against the tables rather than the entity graph: this package
     * does not know about items or recurrence rules, and should not have to in
     * order to let go of their pictures. A rule's own pictures are included, or
     * deleting a list would leave the bytes behind its standing instructions
     * unreachable and unswept.
     */
    @Query(value = """
            SELECT * FROM stored_image
            WHERE item_id IN (SELECT id FROM todo_item WHERE list_id = :listId)
               OR rule_id IN (SELECT id FROM recurrence_rule WHERE list_id = :listId)
            """, nativeQuery = true)
    List<StoredImage> findForList(@Param("listId") UUID listId);

    @Query("SELECT i FROM StoredImage i WHERE i.ruleId = :ruleId")
    List<StoredImage> findForRule(@Param("ruleId") UUID ruleId);

    @Query("SELECT i FROM StoredImage i WHERE i.entryId = :entryId")
    List<StoredImage> findForEntry(@Param("entryId") UUID entryId);

    /**
     * Every picture on every entry of one collection.
     *
     * <p>Native for the same reason {@link #findForList} is: this package does
     * not know about collections and should not have to in order to let go of
     * their pictures.
     */
    @Query(value = """
            SELECT * FROM stored_image
            WHERE entry_id IN (SELECT id FROM data_entry WHERE data_list_id = :listId)
            """, nativeQuery = true)
    List<StoredImage> findForDataList(@Param("listId") UUID listId);
}

