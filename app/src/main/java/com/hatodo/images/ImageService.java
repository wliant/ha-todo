package com.hatodo.images;

import com.hatodo.user.AppUser;
import java.io.InputStream;
import java.time.format.DateTimeFormatter;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Storing and reading images, and keeping the inventory honest about both. */
@Service
public class ImageService {

    /** Comfortably more than a phone photo needs once it has been picked. */
    public static final int MAX_BYTES = 5 * 1024 * 1024;

    private static final DateTimeFormatter PREFIX =
            DateTimeFormatter.ofPattern("yyyy/MM").withZone(ZoneOffset.UTC);

    private final StoredImageRepository images;
    private final ImageStorage storage;

    public ImageService(StoredImageRepository images, ImageStorage storage) {
        this.images = images;
        this.storage = storage;
    }

    @Transactional
    public StoredImage store(byte[] content, ImagePurpose purpose, AppUser uploader) {
        if (content == null || content.length == 0) {
            throw new UnsupportedImageTypeException();
        }
        if (content.length > MAX_BYTES) {
            throw new ImageTooLargeException();
        }

        // Sniffed, so the extension on the stored key describes the bytes
        // rather than whatever the uploader called the file.
        ImageType type = ImageType.sniff(content);
        String objectKey = "%s/%s/%s.%s".formatted(
                purpose.name().toLowerCase(java.util.Locale.ROOT),
                PREFIX.format(java.time.Instant.now()),
                UUID.randomUUID(),
                type.extension());

        storage.put(objectKey, content, type.contentType());
        return images.save(new StoredImage(objectKey, type.contentType(), content.length,
                purpose, uploader));
    }

    @Transactional(readOnly = true)
    public StoredImage require(UUID id) {
        return images.findById(id).orElseThrow(() -> new ImageNotFoundException(id));
    }

    public InputStream open(StoredImage image) {
        return storage.open(image.getObjectKey());
    }

    /**
     * Forgets an image, and deletes its bytes only once the transaction that
     * forgot it has actually committed.
     *
     * <p>The other order loses the bytes behind a row that is still there if
     * the transaction rolls back - and an unreferenced object costs a few
     * kilobytes, where a referenced one that is gone is a broken picture.
     */
    @Transactional
    public void forget(StoredImage image) {
        String objectKey = image.getObjectKey();
        images.delete(image);
        afterCommit(() -> {
            try {
                storage.remove(objectKey);
            } catch (ImageStorageException ignored) {
                // Tidying, not correctness. The row is gone either way.
            }
        });
    }

    private static void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
