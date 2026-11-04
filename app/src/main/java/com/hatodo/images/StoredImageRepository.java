package com.hatodo.images;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoredImageRepository extends JpaRepository<StoredImage, UUID> {

    @org.springframework.data.jpa.repository.Query(
            "SELECT i FROM StoredImage i WHERE i.itemId = :itemId")
    java.util.List<StoredImage> findForItem(
            @org.springframework.data.repository.query.Param("itemId") UUID itemId);
}
