package com.hatodo.images;

/** The object store refused or was unreachable. */
public class ImageStorageException extends RuntimeException {

    public ImageStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
