package com.hatodo.admin;

import com.hatodo.user.AppUser;
import com.hatodo.user.AppUserRepository;
import com.hatodo.user.UserRole;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reconciles the one admin row against {@code ADMIN_USERNAME} /
 * {@code ADMIN_PASSWORD} on every startup, so a freshly created database (prod
 * included, where no seed migration runs) is administrable immediately and the
 * password can be rotated by redeploying.
 *
 * <p>Startup fails if no password is configured. A deployment that runs but
 * cannot be administered is worse than one that refuses to start, because it
 * looks healthy. See docs/adr/0006-admin-account-from-environment.md.
 */
@Component
public class AdminAccountInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminAccountInitializer.class);

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AdminAccountProperties properties;

    public AdminAccountInitializer(AppUserRepository users,
                                   PasswordEncoder passwordEncoder,
                                   AdminAccountProperties properties) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String username = text(properties.username());
        String password = properties.password();
        String displayName = text(properties.displayName());

        if (username == null) {
            throw new IllegalStateException(
                    "ADMIN_USERNAME is not set. The admin account's login name must be configured.");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalStateException(
                    "ADMIN_PASSWORD is not set. The admin account always requires a password, and it is "
                            + "supplied by the environment rather than seeded - see "
                            + "docs/adr/0006-admin-account-from-environment.md.");
        }
        if (displayName == null) {
            displayName = username;
        }

        // Found by role, not by username: changing ADMIN_USERNAME renames the
        // existing admin rather than creating a second one, which the partial
        // unique index would reject anyway.
        Optional<AppUser> existing = users.findByRole(UserRole.ADMIN);
        if (existing.isEmpty()) {
            requireUsernameFree(username);
            users.save(new AppUser(username, displayName, passwordEncoder.encode(password), UserRole.ADMIN));
            log.info("Created the admin account '{}' from the environment", username);
            return;
        }

        AppUser admin = existing.get();
        boolean changed = false;

        if (!admin.getUsername().equalsIgnoreCase(username)) {
            requireUsernameFree(username);
            log.info("Renaming the admin account from '{}' to '{}'", admin.getUsername(), username);
            admin.setUsername(username);
            changed = true;
        }
        if (!displayName.equals(admin.getDisplayName())) {
            admin.setDisplayName(displayName);
            changed = true;
        }
        if (admin.getPasswordHash() == null || !passwordEncoder.matches(password, admin.getPasswordHash())) {
            admin.setPasswordHash(passwordEncoder.encode(password));
            log.info("Updated the admin account's password from the environment");
            changed = true;
        }

        if (changed) {
            users.save(admin);
        }
    }

    private void requireUsernameFree(String username) {
        if (users.existsByUsernameIgnoreCase(username)) {
            throw new IllegalStateException("ADMIN_USERNAME '" + username
                    + "' is already taken by a member account. Choose another, or delete that member.");
        }
    }

    private static String text(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
