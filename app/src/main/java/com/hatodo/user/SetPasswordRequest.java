package com.hatodo.user;

import jakarta.validation.constraints.Size;

/**
 * Sets or clears a password. A null or blank {@code password} clears it, which
 * is how "no password required" is turned on: {@code password_hash IS NULL} is
 * the single source of truth for that, with no companion boolean to contradict
 * it (adr/0005).
 *
 * <p>Shared by the member's own route and the admin's reset route: the body and
 * its meaning are identical, so there is one record rather than two.
 *
 * <p>There is deliberately no current-password field. Half the accounts in this
 * product have none, so the prompt would be unanswerable for exactly the
 * members the feature exists for, and an authenticated session already carries
 * the full authority of the account. See adr/0017.
 */
public record SetPasswordRequest(@Size(max = 100) String password) {

    public boolean wantsPassword() {
        return password != null && !password.isBlank();
    }
}
