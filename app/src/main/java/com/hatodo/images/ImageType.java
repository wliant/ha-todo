package com.hatodo.images;

import java.util.Arrays;

/**
 * The image types this product accepts, recognised by their leading bytes.
 *
 * <p>Sniffed rather than trusted: a declared {@code Content-Type} and a
 * filename are both chosen by whoever is uploading, and the answer decides what
 * the API will serve back from our own origin. SVG is deliberately absent - an
 * SVG served same-origin is script execution in our own security context, which
 * is a stored cross-site scripting bug wearing a profile picture.
 */
public enum ImageType {

    PNG("image/png", "png", new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A}),
    JPEG("image/jpeg", "jpg", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}),
    /** RIFF....WEBP - the four bytes between are the length, so they are skipped. */
    WEBP("image/webp", "webp", new byte[] {'R', 'I', 'F', 'F'});

    private final String contentType;
    private final String extension;
    private final byte[] magic;

    ImageType(String contentType, String extension, byte[] magic) {
        this.contentType = contentType;
        this.extension = extension;
        this.magic = magic;
    }

    public String contentType() {
        return contentType;
    }

    public String extension() {
        return extension;
    }

    /** @throws UnsupportedImageTypeException if the bytes are anything else */
    public static ImageType sniff(byte[] content) {
        for (ImageType type : values()) {
            if (startsWith(content, type.magic)) {
                if (type == WEBP && !startsWith(Arrays.copyOfRange(content,
                        Math.min(8, content.length), content.length), new byte[] {'W', 'E', 'B', 'P'})) {
                    continue;
                }
                return type;
            }
        }
        throw new UnsupportedImageTypeException();
    }

    private static boolean startsWith(byte[] content, byte[] prefix) {
        if (content == null || content.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (content[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
