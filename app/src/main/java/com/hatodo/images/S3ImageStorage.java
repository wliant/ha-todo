package com.hatodo.images;

import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import org.springframework.stereotype.Component;

/**
 * The one class that talks to the object store.
 *
 * <p>The client is MinIO's, but the server is SeaweedFS - minio-java is a plain
 * S3 client and speaks to any S3 API. See docs/adr/0021.
 */
@Component
public class S3ImageStorage implements ImageStorage {

    private final MinioClient client;
    private final String bucket;

    public S3ImageStorage(MinioClient client, ObjectStorageProperties properties) {
        this.client = client;
        this.bucket = properties.bucket();
    }

    @Override
    public void put(String objectKey, byte[] content, String contentType) {
        try (InputStream stream = new ByteArrayInputStream(content)) {
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(stream, content.length, -1)
                    .contentType(contentType)
                    .build());
        } catch (Exception ex) {
            throw new ImageStorageException("Could not store " + objectKey, ex);
        }
    }

    @Override
    public InputStream open(String objectKey) {
        try {
            return client.getObject(GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build());
        } catch (Exception ex) {
            throw new ImageStorageException("Could not read " + objectKey, ex);
        }
    }

    @Override
    public void remove(String objectKey) {
        try {
            client.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build());
        } catch (Exception ex) {
            // Deleting the bytes behind a row that is already gone is tidying,
            // not correctness: losing the race leaves an orphan, and an orphan
            // is cheaper than a failed request the member has to repeat.
            throw new ImageStorageException("Could not remove " + objectKey, ex);
        }
    }
}
