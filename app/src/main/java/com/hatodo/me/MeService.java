package com.hatodo.me;

import com.hatodo.user.AppUser;
import com.hatodo.user.AppUserRepository;
import com.hatodo.user.AdminPasswordProtectedException;
import com.hatodo.user.CurrentUserProvider;
import com.hatodo.user.SetPasswordRequest;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What a member may change about themselves.
 *
 * <p>Every method here acts on {@link CurrentUserProvider#requireUserId()} and
 * none takes a user id, so there is no parameter to tamper with and no
 * authorization decision beyond being authenticated.
 */
@Service
public class MeService {

    private final AppUserRepository users;
    private final CurrentUserProvider currentUser;
    private final PasswordEncoder passwordEncoder;

    public MeService(AppUserRepository users, CurrentUserProvider currentUser,
                     PasswordEncoder passwordEncoder) {
        this.users = users;
        this.currentUser = currentUser;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public PreferencesResponse preferences() {
        return PreferencesResponse.of(require());
    }

    @Transactional
    public PreferencesResponse updatePreferences(PreferencesRequest request) {
        AppUser user = require();
        user.setThemePreference(request.theme());
        return PreferencesResponse.of(user);
    }

    /**
     * Sets or clears the caller's own password. No current password is asked
     * for; see adr/0017 for why, and for the accepted consequence that a
     * passwordless account can be captured this way.
     */
    @Transactional
    public void setPassword(SetPasswordRequest request) {
        AppUser user = require();
        if (user.isAdmin()) {
            throw new AdminPasswordProtectedException();
        }
        user.setPasswordHash(request.wantsPassword() ? passwordEncoder.encode(request.password()) : null);
    }

    private AppUser require() {
        UUID id = currentUser.requireUserId();
        // The principal came from a session this deployment issued, so a missing
        // row means the account was deleted mid-session.
        return users.findById(id).orElseThrow(
                () -> new IllegalStateException("Authenticated user " + id + " no longer exists"));
    }
}
