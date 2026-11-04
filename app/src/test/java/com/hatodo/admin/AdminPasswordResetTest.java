package com.hatodo.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.hatodo.user.AdminPasswordProtectedException;
import com.hatodo.user.AppUser;
import com.hatodo.images.ImageService;
import com.hatodo.user.AppUserRepository;
import com.hatodo.user.SetPasswordRequest;
import com.hatodo.user.UserRole;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * The reset path that makes R33 recoverable: a member can lock themselves - or
 * a sibling - out by setting a password, so the admin must be able to clear it.
 */
@ExtendWith(MockitoExtension.class)
class AdminPasswordResetTest {

    @Mock
    private AppUserRepository users;

    @Mock
    private ImageService images;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    private AdminUserService service() {
        return new AdminUserService(users, encoder, images);
    }

    private UUID stored(AppUser user) {
        UUID id = UUID.randomUUID();
        when(users.findById(id)).thenReturn(Optional.of(user));
        return id;
    }

    @Test
    void clearsAMembersPasswordSoOneTapSignInWorksAgain() {
        AppUser ben = AppUser.member("ben", "Ben", encoder.encode("locked-out"));
        UUID id = stored(ben);

        service().setPassword(id, new SetPasswordRequest(null));

        assertThat(ben.getPasswordHash()).isNull();
        assertThat(ben.isPasswordRequired()).isFalse();
    }

    @Test
    void setsAMembersPassword() {
        AppUser ben = AppUser.passwordlessMember("ben", "Ben");
        UUID id = stored(ben);

        service().setPassword(id, new SetPasswordRequest("fresh-start"));

        assertThat(encoder.matches("fresh-start", ben.getPasswordHash())).isTrue();
    }

    @Test
    void refusesToTouchTheAdminRow() {
        AppUser admin = new AppUser("admin", "Admin", "$2y$10$hash", UserRole.ADMIN);
        UUID id = stored(admin);
        AdminUserService service = service();
        SetPasswordRequest request = new SetPasswordRequest("nope");

        assertThatThrownBy(() -> service.setPassword(id, request))
                .isInstanceOf(AdminPasswordProtectedException.class);
        assertThat(admin.getPasswordHash()).isEqualTo("$2y$10$hash");
    }

    @Test
    void reportsAnUnknownUser() {
        UUID id = UUID.randomUUID();
        when(users.findById(id)).thenReturn(Optional.empty());
        AdminUserService service = service();
        SetPasswordRequest request = new SetPasswordRequest("x");

        assertThatThrownBy(() -> service.setPassword(id, request))
                .isInstanceOf(UserNotFoundException.class);
    }
}
