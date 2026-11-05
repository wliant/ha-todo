package com.hatodo.auth;

import com.hatodo.household.HouseholdProperties;
import com.hatodo.user.AppUser;
import com.hatodo.user.AppUserDetailsService;
import com.hatodo.user.AppUserPrincipal;
import com.hatodo.user.AppUserRepository;
import java.util.List;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Authenticates a sign-in attempt, which for this product has two paths: the
 * ordinary password check, and users for whom no password is required.
 *
 * <p>The branch lives here as one explicit {@code if} rather than inside a
 * custom {@code AuthenticationProvider}, because an authentication bypass
 * should be obvious and directly testable instead of emerging from bean
 * ordering. See
 * docs/adr/0005-pick-a-user-login-with-optional-passwords.md.
 */
@Service
public class LoginService {

    private final AuthenticationManager authenticationManager;
    private final AppUserRepository users;
    private final HouseholdProperties household;

    public LoginService(AuthenticationManager authenticationManager, AppUserRepository users,
                        HouseholdProperties household) {
        this.authenticationManager = authenticationManager;
        this.users = users;
        this.household = household;
    }

    /**
     * @throws org.springframework.security.core.AuthenticationException if the
     *         attempt fails, for any reason - the caller must not distinguish
     *         an unknown user from a wrong password.
     */
    @Transactional(readOnly = true)
    public Authentication authenticate(String username, String password) {
        AppUser user = users.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new BadCredentialsException("Bad credentials"));

        if (user.isPasswordRequired()) {
            return authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(user.getUsername(), password));
        }

        if (!user.isEnabled()) {
            throw new BadCredentialsException("Bad credentials");
        }

        // No password is stored for this user, so there is nothing to verify and
        // any password the client sent is ignored. Being in the roster is the
        // whole credential; the requirement document says so out loud.
        AppUserPrincipal details = AppUserDetailsService.toUserDetails(user);
        return UsernamePasswordAuthenticationToken.authenticated(details, null, details.getAuthorities());
    }

    /** The pick-a-user roster: enabled users only, admin included. */
    @Transactional(readOnly = true)
    public List<LoginUserResponse> roster() {
        return users.findAllByEnabledTrueOrderByDisplayNameAsc().stream()
                .map(LoginUserResponse::of)
                .toList();
    }

    @Transactional(readOnly = true)
    public CurrentUserResponse currentUser(String username) {
        return users.findByUsernameIgnoreCase(username)
                .map(user -> CurrentUserResponse.of(user, household.zone()))
                .orElseThrow(() -> new BadCredentialsException("Bad credentials"));
    }
}
