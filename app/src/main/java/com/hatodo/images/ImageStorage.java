package com.hatodo.images;

import java.io.InputStream;

/**
 * Where image bytes live.
 *
 * <p>An interface so the MinIO client is confined to one implementation - the
 * lock-in adr/0013 accepts is a class, not a codebase.
 */
public interface ImageStorage {

    void put(String objectKey, byte[] content, String contentType);

    /** The caller closes the stream. */
    InputStream open(String objectKey);

    void remove(String objectKey);
}
