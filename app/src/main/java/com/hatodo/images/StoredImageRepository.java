package com.hatodo.images;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoredImageRepository extends JpaRepository<StoredImage, UUID> {
}
