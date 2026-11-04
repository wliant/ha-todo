package com.hatodo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * The admin's user administration, end to end: only the admin reaches it, a
 * created user immediately appears on the sign-in screen with the right
 * password expectation, and the admin account itself cannot be removed.
 */
class AdminUserApiIntegrationTest extends ApiIntegrationTest {

    @Test
    void anonymousCallerCannotListUsers() {
        ResponseEntity<String> response = rest.getForEntity("/api/admin/users", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void anOrdinaryMemberIsForbidden() {
        // Ana is a signed-in household member, not an admin: 403, not 401.
        Session ana = signIn("ana", "password123");

        ResponseEntity<String> response = rest.exchange("/api/admin/users", HttpMethod.GET,
                new HttpEntity<>(headersFor(ana)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void aPasswordlessMemberIsForbiddenToo() {
        Session ben = signIn("ben", null);

        ResponseEntity<String> response = rest.exchange("/api/admin/users", HttpMethod.GET,
                new HttpEntity<>(headersFor(ben)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void adminListsUsersWithoutEverSeeingAHash() {
        Session admin = signIn(ADMIN_USERNAME, ADMIN_PASSWORD);

        ResponseEntity<String> response = rest.exchange("/api/admin/users", HttpMethod.GET,
                new HttpEntity<>(headersFor(admin)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"ana\"").doesNotContain("$2y$");
    }

    @Test
    void addedPasswordlessUserCanSignInByBeingPicked() {
        Session admin = signIn(ADMIN_USERNAME, ADMIN_PASSWORD);

        ResponseEntity<Map> created = createUser(admin, "{\"username\":\"cara\",\"displayName\":\"Cara\"}");

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).containsEntry("passwordRequired", false);

        // Visible on the sign-in screen, and signs in with no password at all.
        assertThat(roster()).anySatisfy(entry ->
                assertThat(entry).containsEntry("username", "cara").containsEntry("passwordRequired", false));
        assertThat(login("cara", null).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void addedUserWithAPasswordMustPresentIt() {
        Session admin = signIn(ADMIN_USERNAME, ADMIN_PASSWORD);

        ResponseEntity<Map> created = createUser(admin,
                "{\"username\":\"dan\",\"displayName\":\"Dan\",\"password\":\"dans-password\"}");

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).containsEntry("passwordRequired", true);

        assertThat(login("dan", null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(login("dan", "wrong").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(login("dan", "dans-password").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void duplicateLoginNameIsRefused() {
        Session admin = signIn(ADMIN_USERNAME, ADMIN_PASSWORD);

        ResponseEntity<Map> response = createUser(admin,
                "{\"username\":\"ana\",\"displayName\":\"Ana Again\"}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void deletedUserDisappearsFromTheSignInScreenAndCanNoLongerSignIn() {
        Session admin = signIn(ADMIN_USERNAME, ADMIN_PASSWORD);
        ResponseEntity<Map> created = createUser(admin,
                "{\"username\":\"evie\",\"displayName\":\"Evie\"}");
        String id = (String) created.getBody().get("id");

        ResponseEntity<Void> deleted = rest.exchange("/api/admin/users/" + id, HttpMethod.DELETE,
                new HttpEntity<>(headersFor(admin)), Void.class);

        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(roster()).noneSatisfy(entry -> assertThat(entry).containsEntry("username", "evie"));
        assertThat(login("evie", null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void theAdminCannotDeleteItself() {
        // R12: the household must not be able to lock itself out of administration.
        Session admin = signIn(ADMIN_USERNAME, ADMIN_PASSWORD);
        String adminId = adminIdFromRoster();

        ResponseEntity<String> response = rest.exchange("/api/admin/users/" + adminId, HttpMethod.DELETE,
                new HttpEntity<>(headersFor(admin)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(login(ADMIN_USERNAME, ADMIN_PASSWORD).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void creatingAUserWithoutACsrfTokenIsRejected() {
        Session admin = signIn(ADMIN_USERNAME, ADMIN_PASSWORD);
        org.springframework.http.HttpHeaders sessionOnly = new org.springframework.http.HttpHeaders();
        sessionOnly.add(org.springframework.http.HttpHeaders.COOKIE, admin.cookie());
        sessionOnly.add(org.springframework.http.HttpHeaders.CONTENT_TYPE, "application/json");

        ResponseEntity<String> response = rest.exchange("/api/admin/users", HttpMethod.POST,
                new HttpEntity<>("{\"username\":\"fred\",\"displayName\":\"Fred\"}", sessionOnly),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void rejectsALoginNameThatIsNotATypeableHandle() {
        Session admin = signIn(ADMIN_USERNAME, ADMIN_PASSWORD);

        ResponseEntity<Map> response = createUser(admin,
                "{\"username\":\"not a handle!\",\"displayName\":\"Nope\"}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private ResponseEntity<Map> createUser(Session admin, String json) {
        return rest.exchange("/api/admin/users", HttpMethod.POST,
                new HttpEntity<>(json, headersFor(admin)), Map.class);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> roster() {
        ResponseEntity<List> response = rest.getForEntity("/api/auth/users", List.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (List<Map<String, Object>>) response.getBody();
    }

    private String adminIdFromRoster() {
        return roster().stream()
                .filter(entry -> "ADMIN".equals(entry.get("role")))
                .map(entry -> (String) entry.get("id"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no admin in the roster"));
    }
}
