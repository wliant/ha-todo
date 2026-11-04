package com.hatodo.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@ExtendWith(MockitoExtension.class)
class AppUserDetailsServiceTest {

    @Mock
    private AppUserRepository users;

    @Test
    void mapsStoredUserOntoUserDetails() {
        when(users.findByUsernameIgnoreCase("ana"))
                .thenReturn(Optional.of(AppUser.member("ana", "Ana", "$2y$10$hash")));

        UserDetails details = new AppUserDetailsService(users).loadUserByUsername("ana");

        assertThat(details.getUsername()).isEqualTo("ana");
        assertThat(details.getPassword()).isEqualTo("$2y$10$hash");
        assertThat(details.isEnabled()).isTrue();
        assertThat(details.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_MEMBER");
    }

    @Test
    void grantsTheAdminRoleFromTheRoleColumn() {
        UserDetails details = AppUserDetailsService.toUserDetails(
                new AppUser("admin", "Admin", "$2y$10$hash", UserRole.ADMIN));

        assertThat(details.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ADMIN");
    }

    @Test
    void givesAPasswordlessUserAnUnmatchablePlaceholderRatherThanNoPassword() {
        // If the passwordless branch in LoginService were ever removed, the
        // ordinary password check must fail closed rather than let anyone in.
        UserDetails details = AppUserDetailsService.toUserDetails(
                AppUser.passwordlessMember("ben", "Ben"));

        assertThat(details.getPassword()).isEqualTo("{password-not-required}");
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        assertThat(encoder.matches("", details.getPassword())).isFalse();
        assertThat(encoder.matches("{password-not-required}", details.getPassword())).isFalse();
    }

    @Test
    void rejectsUnknownUsernameWithoutRevealingWhy() {
        when(users.findByUsernameIgnoreCase("nobody")).thenReturn(Optional.empty());

        AppUserDetailsService service = new AppUserDetailsService(users);

        assertThatThrownBy(() -> service.loadUserByUsername("nobody"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageNotContaining("nobody");
    }
}
