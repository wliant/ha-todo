package com.hatodo;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Real Postgres and a real S3 store for integration tests, both pinned so a new
 * upstream release cannot change what the suite ran against.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    private static final int S3_PORT = 8333;

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));
    }

    /**
     * SeaweedFS in its all-in-one mode, serving the S3 API the application
     * talks. No Testcontainers module for it, so the ports and the readiness
     * check are spelled out here.
     */
    @Bean
    GenericContainer<?> objectStoreContainer() {
        return new GenericContainer<>(DockerImageName.parse("chrislusf/seaweedfs:4.50"))
                .withCommand("server", "-s3", "-dir=/data", "-master.volumeSizeLimitMB=64")
                .withExposedPorts(S3_PORT)
                .waitingFor(Wait.forHttp("/").forPort(S3_PORT).forStatusCode(200));
    }

    /**
     * The application reads its own {@code app.storage.*} properties rather
     * than a Boot-managed client, so the container's address has to be handed
     * to those. The parameter is named for the bean, because a
     * PostgreSQLContainer is a GenericContainer too and the type alone is
     * ambiguous.
     */
    @Bean
    DynamicPropertyRegistrar objectStoreProperties(GenericContainer<?> objectStoreContainer) {
        return registry -> {
            registry.add("app.storage.endpoint",
                    () -> "http://%s:%d".formatted(objectStoreContainer.getHost(),
                            objectStoreContainer.getMappedPort(S3_PORT)));
            registry.add("app.storage.access-key", () -> "itest");
            registry.add("app.storage.secret-key", () -> "itest-secret");
            registry.add("app.storage.bucket", () -> "ha-todo-itest");
        };
    }
}
