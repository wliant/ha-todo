package com.hatodo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * A member's own preferences and password, against real Postgres.
 *
 * <p>Each test makes its own account rather than touching the seeded demo rows:
 * the container and schema are shared across the whole source set, so a test
 * that leaves a password behind breaks the next one's sign-in.
 */
class MeApiIntegrationTest extends ApiIntegrationTest {

    private record Account(String username, String password) {}

    /** @param password null for a member who signs in by being picked. */
    private Account givenMember(String password) {
        String username = "me-" + UUID.randomUUID().toString().substring(0, 8);
        Session admin = signIn(ADMIN_USERNAME, ADMIN_PASSWORD);
        String body = password == null
                ? "{\"username\":\"%s\",\"displayName\":\"Member\"}".formatted(username)
                : "{\"username\":\"%s\",\"displayName\":\"Member\",\"password\":\"%s\"}"
                        .formatted(username, password);
        ResponseEntity<Map> created = rest.exchange("/api/admin/users", HttpMethod.POST,
                new HttpEntity<>(body, headersFor(admin)), Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return new Account(username, password);
    }

    @Test
    void preferencesDefaultToFollowingTheDeviceAndCanBeChanged() {
        Account member = givenMember("password123");
        Session session = signIn(member.username(), member.password());

        ResponseEntity<Map> initial = rest.exchange("/api/me/preferences", HttpMethod.GET,
                new HttpEntity<>(headersFor(session)), Map.class);
        assertThat(initial.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(initial.getBody().get("theme")).isEqualTo("SYSTEM");

        ResponseEntity<Map> updated = rest.exchange("/api/me/preferences", HttpMethod.PATCH,
                new HttpEntity<>("{\"theme\":\"DARK\"}", headersFor(session)), Map.class);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody().get("theme")).isEqualTo("DARK");

        // It is a property of the person, so it survives a new session.
        Session again = signIn(member.username(), member.password());
        ResponseEntity<Map> reread = rest.exchange("/api/me/preferences", HttpMethod.GET,
                new HttpEntity<>(headersFor(again)), Map.class);
        assertThat(reread.getBody().get("theme")).isEqualTo("DARK");
    }

    @Test
    void rejectsAThemeThatIsNotOneOfTheThree() {
        Account member = givenMember("password123");
        Session session = signIn(member.username(), member.password());

        ResponseEntity<Map> response = rest.exchange("/api/me/preferences", HttpMethod.PATCH,
                new HttpEntity<>("{\"theme\":\"CHARTREUSE\"}", headersFor(session)), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void aPasswordlessMemberCanSetTheirOwnPasswordAndThenMustUseIt() {
        Account member = givenMember(null);
        Session session = signIn(member.username(), null);

        ResponseEntity<Void> set = rest.exchange("/api/me/password", HttpMethod.PUT,
                new HttpEntity<>("{\"password\":\"brand-new\"}", headersFor(session)), Void.class);
        assertThat(set.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(login(member.username(), null).getStatusCode())
                .as("a password is required now, so picking the name alone must fail")
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(login(member.username(), "brand-new").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void clearingAPasswordRestoresOneTapSignIn() {
        Account member = givenMember("password123");
        Session session = signIn(member.username(), member.password());

        ResponseEntity<Void> cleared = rest.exchange("/api/me/password", HttpMethod.PUT,
                new HttpEntity<>("{\"password\":null}", headersFor(session)), Void.class);
        assertThat(cleared.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(login(member.username(), null).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void refusesToChangeTheAdminsOwnPassword() {
        Session admin = signIn(ADMIN_USERNAME, ADMIN_PASSWORD);

        ResponseEntity<Map> response = rest.exchange("/api/me/password", HttpMethod.PUT,
                new HttpEntity<>("{\"password\":\"something-else\"}", headersFor(admin)), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat((String) response.getBody().get("error")).contains("deployment configuration");
        // And the environment password still works.
        assertThat(login(ADMIN_USERNAME, ADMIN_PASSWORD).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void theAdminResetsAMemberWhoLockedThemselvesOut() {
        Account member = givenMember(null);
        Session theirs = signIn(member.username(), null);
        rest.exchange("/api/me/password", HttpMethod.PUT,
                new HttpEntity<>("{\"password\":\"now-locked\"}", headersFor(theirs)), Void.class);
        assertThat(login(member.username(), null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        Session admin = signIn(ADMIN_USERNAME, ADMIN_PASSWORD);
        String id = memberId(admin, member.username());
        ResponseEntity<Void> reset = rest.exchange("/api/admin/users/" + id + "/password",
                HttpMethod.PUT, new HttpEntity<>("{\"password\":null}", headersFor(admin)), Void.class);

        assertThat(reset.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(login(member.username(), null).getStatusCode())
                .as("the reset is what makes R33's accepted lockout recoverable")
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void aMemberCannotResetSomeoneElsesPassword() {
        Account victim = givenMember(null);
        Account caller = givenMember("password123");
        Session admin = signIn(ADMIN_USERNAME, ADMIN_PASSWORD);
        String victimId = memberId(admin, victim.username());

        Session theirs = signIn(caller.username(), caller.password());
        ResponseEntity<Map> response = rest.exchange("/api/admin/users/" + victimId + "/password",
                HttpMethod.PUT, new HttpEntity<>("{\"password\":\"hijack\"}", headersFor(theirs)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(login(victim.username(), null).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void refusesToResetTheAdminRow() {
        Session admin = signIn(ADMIN_USERNAME, ADMIN_PASSWORD);
        String adminId = memberId(admin, ADMIN_USERNAME);

        ResponseEntity<Map> response = rest.exchange("/api/admin/users/" + adminId + "/password",
                HttpMethod.PUT, new HttpEntity<>("{\"password\":\"nope\"}", headersFor(admin)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(login(ADMIN_USERNAME, ADMIN_PASSWORD).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void anonymousCallersGetA401RatherThanARedirect() {
        assertThat(rest.getForEntity("/api/me/preferences", Map.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @SuppressWarnings("unchecked")
    private String memberId(Session admin, String username) {
        ResponseEntity<java.util.List> all = rest.exchange("/api/admin/users", HttpMethod.GET,
                new HttpEntity<>(headersFor(admin)), java.util.List.class);
        return ((java.util.List<Map<String, Object>>) all.getBody()).stream()
                .filter(user -> username.equals(user.get("username")))
                .map(user -> (String) user.get("id"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no user " + username));
    }
}
