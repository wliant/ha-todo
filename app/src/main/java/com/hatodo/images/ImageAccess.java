package com.hatodo.images;

import java.util.UUID;

/**
 * Whether the caller may read a picture that belongs to an item.
 *
 * <p>An interface so the images package does not have to know about items or
 * lists, which both already depend on it. The item package supplies the
 * answer.
 */
public interface ImageAccess {

    boolean canReadItem(UUID itemId);
}
