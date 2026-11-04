package com.hatodo.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hatodo.user.AppUser;
import com.hatodo.user.AppUserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

@ExtendWith(MockitoExtension.class)
class LoginServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private AppUserRepository users;

    private LoginService loginService() {
        return new LoginService(authenticationManager, users);
    }

    @Test
    void checksThePasswordWhenTheUserHasOne() {
        AppUser ana = AppUser.member("ana", "Ana", "$2y$10$hash");
        when(users.findByUsernameIgnoreCase("ana")).thenReturn(Optional.of(ana));
        Authentication authenticated = UsernamePasswordAuthenticationToken.authenticated("ana", null, List.of());
        when(authenticationManager.authenticate(any())).thenReturn(authenticated);

        assertThat(loginService().authenticate("ana", "password123")).isSameAs(authenticated);
    }

    @Test
    void signsInAPasswordlessUserWithoutConsultingTheAuthenticationManager() {
        when(users.findByUsernameIgnoreCase("ben"))
                .thenReturn(Optional.of(AppUser.passwordlessMember("ben", "Ben")));

        Authentication authentication = loginService().authenticate("ben", null);

        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(authentication.getName()).isEqualTo("ben");
        assertThat(authentication.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_MEMBER");
        verify(authenticationManager, never()).authenticate(any());
    }

    @Test
    void ignoresAPasswordSentForAUserWhoNeedsNone() {
        when(users.findByUsernameIgnoreCase("ben"))
                .thenReturn(Optional.of(AppUser.passwordlessMember("ben", "Ben")));

        assertThat(loginService().authenticate("ben", "whatever-they-typed").isAuthenticated()).isTrue();
        verify(authenticationManager, never()).authenticate(any());
    }

    @Test
    void refusesAnUnknownUsernameWithoutSayingSo() {
        when(users.findByUsernameIgnoreCase("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> loginService().authenticate("ghost", "anything"))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageNotContaining("ghost");
    }

    @Test
    void rosterReportsWhoNeedsAPasswordAndNeverAHash() {
        when(users.findAllByEnabledTrueOrderByDisplayNameAsc()).thenReturn(List.of(
                AppUser.member("ana", "Ana", "$2y$10$hash"),
                AppUser.passwordlessMember("ben", "Ben")));

        List<LoginUserResponse> roster = loginService().roster();

        assertThat(roster).extracting(LoginUserResponse::username, LoginUserResponse::passwordRequired)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("ana", true),
                        org.assertj.core.groups.Tuple.tuple("ben", false));
        assertThat(roster.toString()).doesNotContain("$2y$10$hash");
    }
}
