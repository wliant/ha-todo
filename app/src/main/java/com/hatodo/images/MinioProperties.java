package com.hatodo.images;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param bucket named explicitly per environment rather than derived from the
 *               active profile: a profile misconfiguration is a plausible
 *               mistake, and it must not be able to put prod's images in dev's
 *               bucket
 */
@ConfigurationProperties(prefix = "app.storage")
public record MinioProperties(String endpoint, String accessKey, String secretKey, String bucket) {

    public boolean isConfigured() {
        return endpoint != null && !endpoint.isBlank()
                && bucket != null && !bucket.isBlank();
    }
}
