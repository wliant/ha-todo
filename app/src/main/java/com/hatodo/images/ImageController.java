package com.hatodo.images;

import java.io.InputStream;
import java.util.Map;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.time.Duration;

/**
 * Image bytes, streamed through the application.
 *
 * <p>Not a presigned URL: MinIO is not published through either HTTPS front
 * door, so such a URL is unreachable from a phone and blocked as mixed content
 * besides - and it would be a capability that outlives a revoked grant. See
 * adr/0013.
 */
@RestController
@RequestMapping("/api/images")
public class ImageController {

    private final ImageService images;
    private final ImageAccess access;

    public ImageController(ImageService images, ImageAccess access) {
        this.images = images;
        this.access = access;
    }

    /**
     * Readable by anyone for an avatar, because the sign-in screen draws faces
     * before there is a session (adr/0019). A detail picture inherits the
     * access rule of the list its item is on, so a private list's pictures are
     * as private as the list.
     */
    @GetMapping("/{id}")
    public ResponseEntity<InputStreamResource> bytes(@PathVariable UUID id) {
        StoredImage image = images.require(id);
        if (!mayRead(image)) {
            // The same answer as a picture that does not exist: which list it
            // belongs to is itself something R19 hides.
            throw new ImageNotFoundException(id);
        }

        InputStream content = images.open(image);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.getContentType()))
                .contentLength(image.getSizeBytes())
                // The id is the version: replacing an image writes a new row
                // with a new id, so this can never go stale.
                .eTag("\"%s\"".formatted(image.getId()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePrivate())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(new InputStreamResource(content));
    }

    private boolean mayRead(StoredImage image) {
        if (image.getPurpose() == ImagePurpose.AVATAR) {
            return true;
        }
        // Unclaimed: uploaded into an item that was never saved, so only the
        // person who uploaded it has any claim on it.
        return image.getItemId() != null && access.canReadItem(image.getItemId());
    }

    @ExceptionHandler(ImageNotFoundException.class)
    ResponseEntity<Map<String, String>> notFound(ImageNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "That image is not here."));
    }
}
