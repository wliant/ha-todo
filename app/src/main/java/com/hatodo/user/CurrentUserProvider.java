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

    /**
     * The caller, if there is one.
     *
     * <p>For the few places where "nobody" is a real answer rather than a bug
     * - a public endpoint deciding whether to show something - instead of a
     * caught exception, which inside a transaction leaves it marked
     * rollback-only whether or not anybody catches it.
     */
    default java.util.Optional<AppUserPrincipal> find() {
        try {
            return java.util.Optional.of(require());
        } catch (IllegalStateException nobody) {
            return java.util.Optional.empty();
        }
    }

    default UUID requireUserId() {
        return require().getId();
    }

    default boolean isAdmin() {
        return require().isAdmin();
    }
}
