package com.hatodo.user;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    /** Username is unique case-insensitively (see ux_app_user_username). */
    @Query("select u from AppUser u where lower(u.username) = lower(:username)")
    Optional<AppUser> findByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCase(String username);

    /**
     * The single admin row. Found by role, not by username, so a changed
     * ADMIN_USERNAME renames the existing admin instead of creating a second
     * one (docs/adr/0006-admin-account-from-environment.md).
     */
    Optional<AppUser> findByRole(UserRole role);

    List<AppUser> findAllByEnabledTrueOrderByDisplayNameAsc();

    List<AppUser> findAllByOrderByDisplayNameAsc();
}
