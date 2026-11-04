package com.hatodo.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class SecurityConfigTest {

    /** The hash the demo seed migration stores for ana@example.com. */
    private static final String SEEDED_HASH = "$2y$10$ca5oBvmXYQKrb9NIiHTdZOMdQE75fh/3/6A7RblMXxX9rRB038IYO";

    private final PasswordEncoder encoder = new SecurityConfig().passwordEncoder();

    @Test
    void encodesAndVerifiesPasswords() {
        String encoded = encoder.encode("password123");

        assertThat(encoded).startsWith("$2");
        assertThat(encoder.matches("password123", encoded)).isTrue();
        assertThat(encoder.matches("wrong", encoded)).isFalse();
    }

    @Test
    void acceptsThe2yHashVariantUsedBySeedData() {
        // The seed hashes are generated with htpasswd, which emits the $2y$
        // prefix rather than Spring's own $2a$. Guards against a seeded demo
        // account that silently cannot log in.
        assertThat(encoder.matches("password123", SEEDED_HASH)).isTrue();
    }
}
