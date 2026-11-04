package com.hatodo.user;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.stereotype.Component;

/**
 * Reads the principal out of the security context.
 *
 * <p>Uses the holder <em>strategy</em> rather than the static accessor, matching
 * {@link com.hatodo.auth.AuthController}, so both agree about where the context
 * lives if that is ever configured away from a thread local.
 */
@Component
public class SecurityContextCurrentUserProvider implements CurrentUserProvider {

    private final SecurityContextHolderStrategy contextHolderStrategy =
            SecurityContextHolder.getContextHolderStrategy();

    @Override
    public AppUserPrincipal require() {
        Authentication authentication = contextHolderStrategy.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("No authenticated principal in the security context");
        }
        Object principal = authentication.getPrincipal();
        if (!(principal instanceof AppUserPrincipal appUser)) {
            // Fail rather than fall back to a username lookup. A principal of
            // another type means the authentication path did not go through
            // AppUserDetailsService, which is a bug worth surfacing - not
            // something to paper over while handing out an authorization
            // subject.
            throw new IllegalStateException(
                    "Principal is not an AppUserPrincipal but " + principal.getClass().getName());
        }
        return appUser;
    }
}
