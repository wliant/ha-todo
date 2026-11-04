package com.hatodo.admin;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The single admin account's credentials, supplied by the environment rather
 * than seeded into the database
 * (docs/adr/0006-admin-account-from-environment.md).
 */
@ConfigurationProperties(prefix = "app.admin")
public record AdminAccountProperties(String username, String password, String displayName) {}
