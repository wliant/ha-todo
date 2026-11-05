package com.hatodo.user;

import java.util.Collection;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * The authenticated principal, carrying the user's id.
 *
 * <p>Spring's own {@code User} carries a username and authorities but no id,
 * and every per-list access check is keyed by id. Putting it on the principal
 * means it travels in the session and no request pays a lookup to discover who
 * is calling. See
 * docs/adr/0016-the-authenticated-principal-carries-the-user-id.md.
 *
 * <p>Both sign-in paths must produce this type. They do, because
 * {@link AppUserDetailsService#toUserDetails} is the only factory and both the
 * password check and the passwordless branch in
 * {@link com.hatodo.auth.LoginService} go through it.
 */
public final class AppUserPrincipal implements UserDetails {

    private static final long serialVersionUID = 1L;

    /**
     * Stands in for the password of a user who needs none. It is not a valid
     * BCrypt hash, so {@code matches} can only ever return false: if the
     * passwordless branch in {@link com.hatodo.auth.LoginService} were ever
     * removed, authentication would fail closed rather than succeed.
     */
    public static final String NO_PASSWORD_PLACEHOLDER = "{password-not-required}";

    private final UUID id;
    private final String username;
    private final String displayName;
    private final UserRole role;
    private final String password;
    private final boolean enabled;

    public AppUserPrincipal(UUID id, String username, String displayName, UserRole role,
                            String password, boolean enabled) {
        this.id = id;
        this.username = username;
        this.displayName = displayName;
        this.role = role;
        this.password = password;
        this.enabled = enabled;
    }

    public static AppUserPrincipal of(AppUser user) {
        return new AppUserPrincipal(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getRole(),
                user.isPasswordRequired() ? user.getPasswordHash() : NO_PASSWORD_PLACEHOLDER,
                user.isEnabled());
    }

    public UUID getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public UserRole getRole() {
        return role;
    }

    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return AuthorityUtils.createAuthorityList("ROLE_" + role.name());
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
