package com.hatodo.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class AppUserPrincipalTest {

    /** The id is database-generated, so a transient entity has none to carry. */
    private static AppUser withId(AppUser user, UUID id) {
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    @Test
    void carriesTheUserIdSoTheServiceLayerNeedsNoLookup() {
        UUID id = UUID.randomUUID();

        AppUserPrincipal principal =
                AppUserPrincipal.of(withId(AppUser.member("ana", "Ana", "$2y$10$hash"), id));

        assertThat(principal.getId()).isEqualTo(id);
        assertThat(principal.getUsername()).isEqualTo("ana");
        assertThat(principal.getDisplayName()).isEqualTo("Ana");
        assertThat(principal.getPassword()).isEqualTo("$2y$10$hash");
        assertThat(principal.isEnabled()).isTrue();
        assertThat(principal.isAdmin()).isFalse();
    }

    @Test
    void derivesTheAuthorityFromTheRoleColumn() {
        AppUserPrincipal member = AppUserPrincipal.of(AppUser.member("ana", "Ana", "$2y$10$hash"));
        AppUserPrincipal admin = AppUserPrincipal.of(
                new AppUser("admin", "Admin", "$2y$10$hash", UserRole.ADMIN));

        assertThat(member.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_MEMBER");
        assertThat(admin.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ADMIN");
        assertThat(admin.isAdmin()).isTrue();
    }

    @Test
    void givesAPasswordlessUserAnUnmatchablePlaceholderRatherThanNoPassword() {
        // If the passwordless branch in LoginService were ever removed, the
        // ordinary password check must fail closed rather than let anyone in.
        AppUserPrincipal principal = AppUserPrincipal.of(AppUser.passwordlessMember("ben", "Ben"));

        assertThat(principal.getPassword()).isEqualTo("{password-not-required}");
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        assertThat(encoder.matches("", principal.getPassword())).isFalse();
        assertThat(encoder.matches("{password-not-required}", principal.getPassword())).isFalse();
    }
}
