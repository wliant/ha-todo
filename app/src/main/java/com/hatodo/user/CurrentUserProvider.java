package com.hatodo.user;

import java.util.UUID;

/**
 * How a service learns who is calling.
 *
 * <p>Services take this rather than an identity argument. An argument can be
 * passed the wrong value and a forgotten one is an unauthenticated write; there
 * is nothing to pass wrongly here. See
 * docs/adr/0016-the-authenticated-principal-carries-the-user-id.md.
 */
public interface CurrentUserProvider {

    /**
     * @throws IllegalStateException if there is no authenticated principal, or
     *         it is not an {@link AppUserPrincipal}. Never returns null and
     *         never invents an anonymous id: the return value is about to be
     *         used as an authorization subject, so the only safe response to
     *         "nobody" is to fail.
     */
    AppUserPrincipal require();

    default UUID requireUserId() {
        return require().getId();
    }

    default boolean isAdmin() {
        return require().isAdmin();
    }
}
