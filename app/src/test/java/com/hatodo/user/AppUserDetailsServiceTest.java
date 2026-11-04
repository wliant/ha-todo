package com.hatodo.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@ExtendWith(MockitoExtension.class)
class AppUserDetailsServiceTest {

    @Mock
    private AppUserRepository users;

    @Test
    void mapsStoredUserOntoUserDetails() {
        when(users.findByEmailIgnoreCase("ana@example.com"))
                .thenReturn(Optional.of(new AppUser("ana@example.com", "Ana", "$2y$10$hash")));

        UserDetails details = new AppUserDetailsService(users).loadUserByUsername("ana@example.com");

        assertThat(details.getUsername()).isEqualTo("ana@example.com");
        assertThat(details.getPassword()).isEqualTo("$2y$10$hash");
        assertThat(details.isEnabled()).isTrue();
    }

    @Test
    void rejectsUnknownEmailWithoutRevealingWhy() {
        when(users.findByEmailIgnoreCase("nobody@example.com")).thenReturn(Optional.empty());

        AppUserDetailsService service = new AppUserDetailsService(users);

        assertThatThrownBy(() -> service.loadUserByUsername("nobody@example.com"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageNotContaining("nobody@example.com");
    }
}
