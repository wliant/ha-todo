package com.hatodo.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hatodo.user.AppUser;
import com.hatodo.user.AppUserRepository;
import com.hatodo.user.UserRole;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AdminAccountInitializerTest {

    @Mock
    private AppUserRepository users;

    private final PasswordEncoder encoder = new BCryptPasswordEncoder();

    private AdminAccountInitializer initializer(String username, String password, String displayName) {
        return new AdminAccountInitializer(users, encoder,
                new AdminAccountProperties(username, password, displayName));
    }

    private AppUser saved() {
        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(users).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void createsTheAdminOnAnEmptyDatabase() {
        when(users.findByRole(UserRole.ADMIN)).thenReturn(Optional.empty());
        when(users.existsByUsernameIgnoreCase("boss")).thenReturn(false);

        initializer("boss", "s3cret", "The Boss").run(null);

        AppUser admin = saved();
        assertThat(admin.getUsername()).isEqualTo("boss");
        assertThat(admin.getDisplayName()).isEqualTo("The Boss");
        assertThat(admin.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(encoder.matches("s3cret", admin.getPasswordHash())).isTrue();
    }

    @Test
    void refusesToStartWithoutAPassword() {
        // A deployment that runs but cannot be administered looks healthy, which
        // is worse than one that refuses to start.
        assertThatThrownBy(() -> initializer("admin", "  ", "Admin").run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADMIN_PASSWORD");
        verify(users, never()).save(any());
    }

    @Test
    void rotatesThePasswordWhenTheConfiguredOneChanged() {
        AppUser existing = new AppUser("admin", "Admin", encoder.encode("old-password"), UserRole.ADMIN);
        when(users.findByRole(UserRole.ADMIN)).thenReturn(Optional.of(existing));

        initializer("admin", "new-password", "Admin").run(null);

        assertThat(encoder.matches("new-password", saved().getPasswordHash())).isTrue();
    }

    @Test
    void leavesAnUnchangedAdminAlone() {
        AppUser existing = new AppUser("admin", "Admin", encoder.encode("same-password"), UserRole.ADMIN);
        when(users.findByRole(UserRole.ADMIN)).thenReturn(Optional.of(existing));

        initializer("admin", "same-password", "Admin").run(null);

        verify(users, never()).save(any());
    }

    @Test
    void renamesTheExistingAdminRatherThanCreatingASecondOne() {
        AppUser existing = new AppUser("admin", "Admin", encoder.encode("pw"), UserRole.ADMIN);
        when(users.findByRole(UserRole.ADMIN)).thenReturn(Optional.of(existing));
        when(users.existsByUsernameIgnoreCase("owner")).thenReturn(false);

        initializer("owner", "pw", "Admin").run(null);

        assertThat(saved()).isSameAs(existing);
        assertThat(existing.getUsername()).isEqualTo("owner");
    }

    @Test
    void refusesToStealALoginNameAMemberAlreadyHas() {
        when(users.findByRole(UserRole.ADMIN)).thenReturn(Optional.empty());
        when(users.existsByUsernameIgnoreCase("ana")).thenReturn(true);

        assertThatThrownBy(() -> initializer("ana", "pw", "Admin").run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already taken");
        verify(users, never()).save(any());
    }
}
