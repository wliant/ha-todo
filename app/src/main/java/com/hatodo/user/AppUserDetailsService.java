package com.hatodo.user;

import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppUserDetailsService implements org.springframework.security.core.userdetails.UserDetailsService {

    private final AppUserRepository users;

    public AppUserDetailsService(AppUserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public AppUserPrincipal loadUserByUsername(String username) throws UsernameNotFoundException {
        AppUser user = users.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new UsernameNotFoundException("No user for username"));
        return toUserDetails(user);
    }

    /**
     * The only place an authenticated principal is built. Both sign-in paths go
     * through here - the password check via this class, and the passwordless
     * branch in {@link com.hatodo.auth.LoginService} by calling it directly - so
     * neither can end up with a principal the service layer cannot read.
     */
    public static AppUserPrincipal toUserDetails(AppUser user) {
        return AppUserPrincipal.of(user);
    }
}
