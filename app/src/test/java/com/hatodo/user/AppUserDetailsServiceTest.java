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
import org.springframework.security.core.userdetails.UsernameNotFoundException;

/**
 * The lookup itself. How a stored user maps onto an authenticated principal is
 * {@link AppUserPrincipalTest}'s subject.
 */
@ExtendWith(MockitoExtension.class)
class AppUserDetailsServiceTest {

    @Mock
    private AppUserRepository users;

    @Test
    void mapsStoredUserOntoTheAuthenticatedPrincipal() {
        when(users.findByUsernameIgnoreCase("ana"))
                .thenReturn(Optional.of(AppUser.member("ana", "Ana", "$2y$10$hash")));

        AppUserPrincipal details = new AppUserDetailsService(users).loadUserByUsername("ana");

        assertThat(details.getUsername()).isEqualTo("ana");
        assertThat(details.getPassword()).isEqualTo("$2y$10$hash");
        assertThat(details.isEnabled()).isTrue();
        assertThat(details.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_MEMBER");
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
