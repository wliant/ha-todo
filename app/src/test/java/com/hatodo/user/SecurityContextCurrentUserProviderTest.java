package com.hatodo.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * The provider hands out an authorization subject, so every path that cannot
 * produce a real one must throw rather than return a null or an anonymous id.
 */
class SecurityContextCurrentUserProviderTest {

    private final SecurityContextCurrentUserProvider provider = new SecurityContextCurrentUserProvider();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private static void authenticateWith(Authentication authentication) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
    }

    @Test
    void readsTheIdStraightOffThePrincipal() {
        UUID id = UUID.randomUUID();
        AppUserPrincipal principal =
                new AppUserPrincipal(id, "ana", "Ana", UserRole.MEMBER, "$2y$10$hash", true);
        authenticateWith(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities()));

        assertThat(provider.requireUserId()).isEqualTo(id);
        assertThat(provider.require()).isSameAs(principal);
        assertThat(provider.isAdmin()).isFalse();
    }

    @Test
    void reportsTheAdminRoleFromThePrincipal() {
        AppUserPrincipal admin = new AppUserPrincipal(
                UUID.randomUUID(), "admin", "Admin", UserRole.ADMIN, "$2y$10$hash", true);
        authenticateWith(UsernamePasswordAuthenticationToken.authenticated(
                admin, null, admin.getAuthorities()));

        assertThat(provider.isAdmin()).isTrue();
    }

    @Test
    void throwsWhenThereIsNoAuthenticationAtAll() {
        assertThatThrownBy(provider::require)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No authenticated principal");
    }

    @Test
    void throwsForAnAnonymousCaller() {
        authenticateWith(new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        // Anonymous authentication is "authenticated" as far as Spring is
        // concerned, so the type check is what stops it, not the flag.
        assertThatThrownBy(provider::require).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void throwsWhenThePrincipalIsAStringRatherThanOurs() {
        // What an authentication path that skipped AppUserDetailsService would
        // leave behind. There is no id to be had, so falling back silently
        // would mean guessing at an authorization subject.
        authenticateWith(UsernamePasswordAuthenticationToken.authenticated("ana", null, List.of()));

        assertThatThrownBy(provider::require)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not an AppUserPrincipal");
    }

    @Test
    void throwsWhenTheAuthenticationIsNotAuthenticated() {
        authenticateWith(UsernamePasswordAuthenticationToken.unauthenticated("ana", "password"));

        assertThatThrownBy(provider::require).isInstanceOf(IllegalStateException.class);
    }
}
