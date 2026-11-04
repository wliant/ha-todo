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
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock
    private AppUserRepository users;

    private final PasswordEncoder encoder = new BCryptPasswordEncoder();

    private AdminUserService service() {
        return new AdminUserService(users, encoder);
    }

    private AppUser saved() {
        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(users).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void storesAHashWhenTheAdminGaveTheUserAPassword() {
        when(users.existsByUsernameIgnoreCase("cara")).thenReturn(false);
        when(users.save(any())).thenAnswer(call -> call.getArgument(0));

        service().create(new CreateUserRequest("cara", "Cara", "a-password"));

        AppUser created = saved();
        assertThat(created.isPasswordRequired()).isTrue();
        assertThat(created.getPasswordHash()).isNotEqualTo("a-password");
        assertThat(encoder.matches("a-password", created.getPasswordHash())).isTrue();
        assertThat(created.getRole()).isEqualTo(UserRole.MEMBER);
    }

    @Test
    void storesNoHashAtAllWhenNoPasswordIsRequired() {
        when(users.existsByUsernameIgnoreCase("dan")).thenReturn(false);
        when(users.save(any())).thenAnswer(call -> call.getArgument(0));

        AdminUserResponse response = service().create(new CreateUserRequest("dan", "Dan", null));

        assertThat(response.passwordRequired()).isFalse();
        assertThat(saved().getPasswordHash()).isNull();
    }

    @Test
    void treatsABlankPasswordAsNoPasswordRatherThanAnEmptyOne() {
        when(users.existsByUsernameIgnoreCase("dan")).thenReturn(false);
        when(users.save(any())).thenAnswer(call -> call.getArgument(0));

        service().create(new CreateUserRequest("dan", "Dan", "   "));

        assertThat(saved().getPasswordHash()).isNull();
    }

    @Test
    void refusesADuplicateLoginName() {
        when(users.existsByUsernameIgnoreCase("ana")).thenReturn(true);

        assertThatThrownBy(() -> service().create(new CreateUserRequest("ana", "Ana Again", null)))
                .isInstanceOf(UsernameTakenException.class);
        verify(users, never()).save(any());
    }

    @Test
    void refusesToDeleteTheAdminAccount() {
        UUID id = UUID.randomUUID();
        when(users.findById(id))
                .thenReturn(Optional.of(new AppUser("admin", "Admin", "$2y$10$hash", UserRole.ADMIN)));

        assertThatThrownBy(() -> service().delete(id))
                .isInstanceOf(AdminAccountProtectedException.class);
        verify(users, never()).delete(any());
    }

    @Test
    void deletesAMember() {
        UUID id = UUID.randomUUID();
        AppUser ben = AppUser.passwordlessMember("ben", "Ben");
        when(users.findById(id)).thenReturn(Optional.of(ben));

        service().delete(id);

        verify(users).delete(ben);
    }

    @Test
    void reportsAMissingUserRatherThanSucceedingQuietly() {
        UUID id = UUID.randomUUID();
        when(users.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().delete(id)).isInstanceOf(UserNotFoundException.class);
    }
}
