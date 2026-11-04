package com.hatodo.images;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class MinioConfiguration {

    @Bean
    MinioClient minioClient(MinioProperties properties) {
        if (!properties.isConfigured()) {
            // Same fail-closed choice as the admin account: a deployment that
            // starts and silently cannot store an image is a bug report nobody
            // can diagnose.
            throw new IllegalStateException(
                    "MINIO_ENDPOINT and MINIO_BUCKET are required. See docs/adr/0013.");
        }
        return MinioClient.builder()
                .endpoint(properties.endpoint())
                .credentials(properties.accessKey(), properties.secretKey())
                .build();
    }

    /**
     * Creates this environment's bucket if it is not there.
     *
     * <p>An application runner rather than a step in a deploy script, because
     * adr/0004 forbids hand-run SSH work and an undiscoverable manual
     * prerequisite is how a fresh environment fails mysteriously. Startup fails
     * if the store is unreachable - see adr/0013 for the trade that accepts.
     */
    @Bean
    ApplicationRunner minioBucketInitializer(MinioClient client, MinioProperties properties) {
        return (ApplicationArguments args) -> {
            String bucket = properties.bucket();
            try {
                if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                    client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                }
            } catch (Exception ex) {
                throw new IllegalStateException(
                        "Could not reach the object store or create bucket " + bucket, ex);
            }
        };
    }
}
