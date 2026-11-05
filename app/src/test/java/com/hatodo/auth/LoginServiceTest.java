package com.hatodo.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hatodo.household.HouseholdProperties;
import com.hatodo.user.AppUser;
import com.hatodo.user.AppUserPrincipal;
import com.hatodo.user.AppUserRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class LoginServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private AppUserRepository users;

    private LoginService loginService() {
        return new LoginService(authenticationManager, users,
                new HouseholdProperties("Asia/Singapore"));
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
    void givesThePasswordlessBranchAPrincipalCarryingTheUserId() {
        // The two sign-in paths build their Authentication differently. If only
        // the password path yields an AppUserPrincipal, password sign-in works
        // and passwordless sign-in puts a principal in the session that every
        // later authorization check rejects - visible only after sign-in, and
        // only for the members who need no password. See adr/0016.
        UUID id = UUID.randomUUID();
        AppUser ben = AppUser.passwordlessMember("ben", "Ben");
        ReflectionTestUtils.setField(ben, "id", id);
        when(users.findByUsernameIgnoreCase("ben")).thenReturn(Optional.of(ben));

        Authentication authentication = loginService().authenticate("ben", null);

        assertThat(authentication.getPrincipal()).isInstanceOf(AppUserPrincipal.class);
        assertThat(((AppUserPrincipal) authentication.getPrincipal()).getId()).isEqualTo(id);
    }

    @Test
    void refusesAnUnknownUsernameWithoutSayingSo() {
        when(users.findByUsernameIgnoreCase("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> loginService().authenticate("ghost", "anything"))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageNotContaining("ghost");
    }

    @Test
    void rosterSaysOnlyWhoExists() {
        when(users.findAllByEnabledTrueOrderByDisplayNameAsc()).thenReturn(List.of(
                AppUser.member("ana", "Ana", "$2y$10$hash"),
                AppUser.passwordlessMember("ben", "Ben")));

        List<LoginUserResponse> roster = loginService().roster();

        assertThat(roster).extracting(LoginUserResponse::username, LoginUserResponse::displayName)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("ana", "Ana"),
                        org.assertj.core.groups.Tuple.tuple("ben", "Ben"));
        // Anyone who can reach the deployment reads this, so it must not say who
        // is the admin, nor who needs no password (adr/0009). The avatar is the
        // single exception ever argued for - it says nothing about what an
        // account is worth attacking for (adr/0019). Adding anything else to
        // this list needs an ADR of its own, not just a passing test.
        assertThat(LoginUserResponse.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .containsExactly("id", "username", "displayName", "avatarImageId");
        assertThat(roster.toString()).doesNotContain("$2y$10$hash");
    }

    @Test
    void tellsTheClientTheHouseholdsZoneRatherThanLeavingItToTheDevice() {
        AppUser ana = AppUser.member("ana", "Ana", null);
        when(users.findByUsernameIgnoreCase("ana")).thenReturn(Optional.of(ana));

        assertThat(loginService().currentUser("ana").timezone()).isEqualTo("Asia/Singapore");
    }

    @Test
    void fallsBackToTheHouseholdsOwnZoneRatherThanToUtc() {
        // An unset APP_TIMEZONE used to mean UTC, which put the household's
        // evening in the wrong day - see docs/adr/0036.
        assertThat(new HouseholdProperties(null).zone().getId()).isEqualTo("Asia/Singapore");
        assertThat(new HouseholdProperties("  ").zone().getId()).isEqualTo("Asia/Singapore");
    }
}
