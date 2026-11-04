package com.hatodo.me;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.hatodo.user.AdminPasswordProtectedException;
import com.hatodo.user.AppUser;
import com.hatodo.user.AppUserPrincipal;
import com.hatodo.user.AppUserRepository;
import com.hatodo.user.CurrentUserProvider;
import com.hatodo.user.SetPasswordRequest;
import com.hatodo.user.ThemePreference;
import com.hatodo.user.UserRole;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MeServiceTest {

    @Mock
    private AppUserRepository users;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    private MeService serviceFor(AppUser caller) {
        UUID id = caller.getId() != null ? caller.getId() : UUID.randomUUID();
        ReflectionTestUtils.setField(caller, "id", id);
        when(users.findById(id)).thenReturn(Optional.of(caller));
        CurrentUserProvider currentUser = () -> AppUserPrincipal.of(caller);
        return new MeService(users, currentUser, encoder);
    }

    @Test
    void readsAndUpdatesTheOwnersThemePreference() {
        AppUser ana = AppUser.member("ana", "Ana", "$2y$10$hash");
        MeService me = serviceFor(ana);

        assertThat(me.preferences().theme()).isEqualTo(ThemePreference.SYSTEM);
        assertThat(me.updatePreferences(new PreferencesRequest(ThemePreference.DARK)).theme())
                .isEqualTo(ThemePreference.DARK);
        assertThat(ana.getThemePreference()).isEqualTo(ThemePreference.DARK);
    }

    @Test
    void setsAPasswordForAMemberWhoHadNone() {
        AppUser ben = AppUser.passwordlessMember("ben", "Ben");
        MeService me = serviceFor(ben);

        me.setPassword(new SetPasswordRequest("hunter2"));

        assertThat(ben.isPasswordRequired()).isTrue();
        assertThat(encoder.matches("hunter2", ben.getPasswordHash())).isTrue();
    }

    @Test
    void clearingThePasswordTurnsTheRequirementOffEntirely() {
        // password_hash IS NULL *is* "no password required" - there is no
        // companion boolean to set, which is the point of adr/0005.
        AppUser ana = AppUser.member("ana", "Ana", encoder.encode("password123"));
        MeService me = serviceFor(ana);

        me.setPassword(new SetPasswordRequest(null));

        assertThat(ana.getPasswordHash()).isNull();
        assertThat(ana.isPasswordRequired()).isFalse();
    }

    @Test
    void treatsABlankPasswordAsClearingItRatherThanSettingAnEmptyOne() {
        AppUser ana = AppUser.member("ana", "Ana", encoder.encode("password123"));
        MeService me = serviceFor(ana);

        me.setPassword(new SetPasswordRequest("   "));

        assertThat(ana.getPasswordHash()).isNull();
    }

    @Test
    void refusesToChangeTheAdminsOwnPassword() {
        // It is reconciled from ADMIN_PASSWORD on every startup, so accepting
        // this would silently discard it at the next deploy (adr/0006).
        AppUser admin = new AppUser("admin", "Admin", "$2y$10$hash", UserRole.ADMIN);
        MeService me = serviceFor(admin);

        assertThatThrownBy(() -> me.setPassword(new SetPasswordRequest("something")))
                .isInstanceOf(AdminPasswordProtectedException.class);
        assertThat(admin.getPasswordHash()).isEqualTo("$2y$10$hash");
    }
}
