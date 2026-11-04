package com.hatodo.images;

/**
 * The bytes are not one of the three types this product serves.
 *
 * <p>Decided by sniffing the content, never by the declared Content-Type or the
 * filename: both are the uploader's to choose, and this answer decides what the
 * API will later serve from our own origin.
 */
public class UnsupportedImageTypeException extends RuntimeException {

    public UnsupportedImageTypeException() {
        super("Images must be PNG, JPEG or WebP");
    }
}
