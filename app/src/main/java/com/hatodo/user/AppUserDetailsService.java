package com.hatodo.user;

import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppUserDetailsService implements UserDetailsService {

    /**
     * Stands in for the password of a user who needs none. It is not a valid
     * BCrypt hash, so {@code matches} can only ever return false: if the
     * passwordless branch in {@link com.hatodo.auth.LoginService} were ever
     * removed, authentication would fail closed rather than succeed.
     */
    static final String NO_PASSWORD_PLACEHOLDER = "{password-not-required}";

    private final AppUserRepository users;

    public AppUserDetailsService(AppUserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AppUser user = users.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new UsernameNotFoundException("No user for username"));
        return toUserDetails(user);
    }

    public static UserDetails toUserDetails(AppUser user) {
        return User.withUsername(user.getUsername())
                .password(user.isPasswordRequired() ? user.getPasswordHash() : NO_PASSWORD_PLACEHOLDER)
                .disabled(!user.isEnabled())
                .authorities(AuthorityUtils.createAuthorityList("ROLE_" + user.getRole().name()))
                .build();
    }
}
