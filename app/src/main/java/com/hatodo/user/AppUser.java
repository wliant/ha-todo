package com.hatodo.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import com.hatodo.images.StoredImage;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String username;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    /**
     * Null means this user signs in by being picked, with no secret asked for
     * and none stored. There is deliberately no separate "password required"
     * flag that could contradict this column - see
     * docs/adr/0005-pick-a-user-login-with-optional-passwords.md.
     */
    @Column(name = "password_hash")
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role = UserRole.MEMBER;

    @Column(nullable = false)
    private boolean enabled = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "theme_preference", nullable = false)
    private ThemePreference themePreference = ThemePreference.SYSTEM;

    /** Null means initials are drawn instead. Nobody is obliged to have a face. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "avatar_image_id")
    private StoredImage avatarImage;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    protected AppUser() {
        // for JPA
    }

    public AppUser(String username, String displayName, String passwordHash, UserRole role) {
        this.username = username;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.role = role;
    }

    /** A member who signs in by being picked; no password is stored. */
    public static AppUser passwordlessMember(String username, String displayName) {
        return new AppUser(username, displayName, null, UserRole.MEMBER);
    }

    public static AppUser member(String username, String displayName, String passwordHash) {
        return new AppUser(username, displayName, passwordHash, UserRole.MEMBER);
    }

    public UUID getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    /** True when sign-in must present a password, i.e. a hash is stored. */
    public boolean isPasswordRequired() {
        return passwordHash != null;
    }

    public UserRole getRole() {
        return role;
    }

    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public StoredImage getAvatarImage() {
        return avatarImage;
    }

    public void setAvatarImage(StoredImage avatarImage) {
        this.avatarImage = avatarImage;
    }

    /** The id the client turns into /api/images/{id}, or null. */
    public UUID getAvatarImageId() {
        return avatarImage == null ? null : avatarImage.getId();
    }

    public ThemePreference getThemePreference() {
        return themePreference;
    }

    public void setThemePreference(ThemePreference themePreference) {
        this.themePreference = themePreference;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
