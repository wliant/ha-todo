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
     * does not know about items, and should not have to in order to let go of
     * their pictures.
     */
    @Query(value = """
            SELECT * FROM stored_image
            WHERE item_id IN (SELECT id FROM todo_item WHERE list_id = :listId)
            """, nativeQuery = true)
    List<StoredImage> findForList(@Param("listId") UUID listId);
}
