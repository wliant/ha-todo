package com.hatodo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Exercises the whole sign-in path against real Postgres: the roster the
 * pick-a-user screen is drawn from, CSRF token issue, both login paths
 * (password and passwordless), session-backed /api/me, and rejection of
 * anonymous callers.
 */
class AuthFlowIntegrationTest extends ApiIntegrationTest {

    @Test
    void healthIsPublic() {
        ResponseEntity<Map> response = rest.getForEntity("/actuator/health", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("status", "UP");
    }

    @Test
    void anonymousCallerGets401RatherThanALoginRedirect() {
        ResponseEntity<String> response = rest.getForEntity("/api/me", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void rosterIsReadableAnonymouslyAndSaysOnlyWhoExists() {
        // The sign-in screen cannot be drawn without this, so it is public by
        // necessity - which is exactly why its payload must stay minimal. No
        // role, and no password expectation: see adr/0009.
        ResponseEntity<List> response = rest.getForEntity("/api/auth/users", List.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSizeGreaterThanOrEqualTo(3);
        assertThat(response.getBody().toString())
                .doesNotContain("passwordHash")
                .doesNotContain("$2y$")
                .doesNotContain("passwordRequired")
                .doesNotContain("role")
                .doesNotContain("ADMIN");

        assertThat(userNamed(response.getBody(), "ana"))
                .containsOnlyKeys("id", "username", "displayName", "avatarImageId")
                .containsEntry("displayName", "Ana");
        assertThat(userNamed(response.getBody(), "admin")).containsOnlyKeys("id", "username", "displayName", "avatarImageId");
    }

    @Test
    void postingNoPasswordForAnAccountThatNeedsOneIsRefused() {
        // How the client discovers a password is needed, now that the roster no
        // longer tells it: it tries without one and reads the 401 (adr/0009).
        Csrf csrf = fetchCsrf();
        ResponseEntity<Map> login = rest.exchange("/api/auth/login", HttpMethod.POST,
                new HttpEntity<>("{\"username\":\"ana\"}", jsonHeaders(csrf)), Map.class);

        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void adminAccountIsCreatedFromTheEnvironmentAndCanSignIn() {
        ResponseEntity<Map> login = login(ADMIN_USERNAME, ADMIN_PASSWORD);

        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(login.getBody()).containsEntry("role", "ADMIN");
    }

    @Test
    void seededUserCanLogInAndReadTheirOwnProfile() {
        ResponseEntity<Map> login = login("ana", "password123");

        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(login.getBody()).containsEntry("displayName", "Ana");
        assertThat(login.getBody()).containsEntry("role", "MEMBER");

        String session = sessionCookie(login.getHeaders());
        assertThat(session).as("login must establish a session cookie").isNotNull();

        HttpHeaders authed = new HttpHeaders();
        authed.add(HttpHeaders.COOKIE, session);
        ResponseEntity<Map> me = rest.exchange("/api/me", HttpMethod.GET, new HttpEntity<>(authed), Map.class);

        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(me.getBody()).containsEntry("username", "ana");
    }

    @Test
    void wrongPasswordIsRejected() {
        assertThat(login("ana", "nope").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void unknownUsernameIsRejectedTheSameWayAsAWrongPassword() {
        assertThat(login("ghost", "nope").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void passwordlessUserSignsInWithNoPasswordAtAll() {
        Csrf csrf = fetchCsrf();
        ResponseEntity<Map> login = rest.exchange("/api/auth/login", HttpMethod.POST,
                new HttpEntity<>("{\"username\":\"ben\"}", jsonHeaders(csrf)), Map.class);

        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(login.getBody()).containsEntry("displayName", "Ben");
        assertThat(sessionCookie(login.getHeaders())).isNotNull();
    }

    @Test
    void passwordlessUserSignsInEvenIfAPasswordIsSent() {
        // Nothing is stored to check it against, so it is ignored rather than
        // being treated as a mismatch.
        assertThat(login("ben", "irrelevant").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void loginWithoutCsrfTokenIsRejected() {
        // CsrfFilter rejects via its own AccessDeniedHandler, so this is a 403
        // rather than going through the 401 authentication entry point. It is
        // what stops a hostile page silently signing a visitor in as a
        // passwordless member.
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_TYPE, "application/json");

        ResponseEntity<String> login = rest.exchange("/api/auth/login", HttpMethod.POST,
                new HttpEntity<>("{\"username\":\"ben\"}", headers), String.class);

        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> userNamed(List<?> roster, String username) {
        return (Map<String, Object>) roster.stream()
                .map(entry -> (Map<String, Object>) entry)
                .filter(entry -> username.equals(entry.get("username")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("roster has no user " + username + ": " + roster));
    }

}
