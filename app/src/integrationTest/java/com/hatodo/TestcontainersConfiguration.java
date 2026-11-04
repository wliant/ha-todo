package com.hatodo;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Real Postgres and real MinIO for integration tests.
 *
 * <p>Postgres is pinned so a new upstream release cannot change what the suite
 * ran against. MinIO is not, and cannot easily be: its own Docker Hub images
 * refuse anonymous pulls, so this uses Chainguard's rebuild of the same server,
 * whose free tier publishes only {@code :latest}. Mirroring a pinned tag into
 * the project's registry would fix it.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));
    }

    @Bean
    MinIOContainer minioContainer() {
        return new MinIOContainer(DockerImageName.parse("chainguard/minio:latest")
                .asCompatibleSubstituteFor("minio/minio"));
    }

    /**
     * No {@code @ServiceConnection} for MinIO: the application reads its own
     * {@code app.storage.*} properties rather than a Boot-managed client, so
     * the container's address has to be handed to those.
     */
    @Bean
    DynamicPropertyRegistrar minioProperties(MinIOContainer minio) {
        return registry -> {
            registry.add("app.storage.endpoint", minio::getS3URL);
            registry.add("app.storage.access-key", minio::getUserName);
            registry.add("app.storage.secret-key", minio::getPassword);
            registry.add("app.storage.bucket", () -> "ha-todo-itest");
        };
    }
}
