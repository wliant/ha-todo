package com.hatodo.images;

import java.util.UUID;

/** Also what a caller who may not read this image is told. */
public class ImageNotFoundException extends RuntimeException {

    public ImageNotFoundException(UUID id) {
        super("No image " + id + " readable by this caller");
    }
}
