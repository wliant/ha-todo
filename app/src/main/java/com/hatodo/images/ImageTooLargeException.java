package com.hatodo.images;

/** Bigger than {@link ImageService#MAX_BYTES}. */
public class ImageTooLargeException extends RuntimeException {

    public ImageTooLargeException() {
        super("That image is too large");
    }
}
