package com.hatodo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

/**
 * Exercises the whole login path against real Postgres: CSRF token issue,
 * JSON login, session-backed /api/me, and rejection of anonymous callers.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("itest")
@Import(TestcontainersConfiguration.class)
class AuthFlowIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

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
    void seededUserCanLogInAndReadTheirOwnProfile() {
        Csrf csrf = fetchCsrf();

        HttpHeaders loginHeaders = new HttpHeaders();
        loginHeaders.add(HttpHeaders.COOKIE, csrf.cookie());
        loginHeaders.add("X-XSRF-TOKEN", csrf.token());
        loginHeaders.add(HttpHeaders.CONTENT_TYPE, "application/json");

        ResponseEntity<Map> login = rest.exchange("/api/auth/login", HttpMethod.POST,
                new HttpEntity<>("{\"email\":\"ana@example.com\",\"password\":\"password123\"}", loginHeaders),
                Map.class);

        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(login.getBody()).containsEntry("displayName", "Ana");

        String session = sessionCookie(login.getHeaders());
        assertThat(session).as("login must establish a session cookie").isNotNull();

        HttpHeaders authed = new HttpHeaders();
        authed.add(HttpHeaders.COOKIE, session);
        ResponseEntity<Map> me = rest.exchange("/api/me", HttpMethod.GET, new HttpEntity<>(authed), Map.class);

        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(me.getBody()).containsEntry("email", "ana@example.com");
    }

    @Test
    void wrongPasswordIsRejected() {
        Csrf csrf = fetchCsrf();

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, csrf.cookie());
        headers.add("X-XSRF-TOKEN", csrf.token());
        headers.add(HttpHeaders.CONTENT_TYPE, "application/json");

        ResponseEntity<String> login = rest.exchange("/api/auth/login", HttpMethod.POST,
                new HttpEntity<>("{\"email\":\"ana@example.com\",\"password\":\"nope\"}", headers),
                String.class);

        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void loginWithoutCsrfTokenIsRejected() {
        // CsrfFilter rejects via its own AccessDeniedHandler, so this is a 403
        // rather than going through the 401 authentication entry point.
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_TYPE, "application/json");

        ResponseEntity<String> login = rest.exchange("/api/auth/login", HttpMethod.POST,
                new HttpEntity<>("{\"email\":\"ana@example.com\",\"password\":\"password123\"}", headers),
                String.class);

        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private record Csrf(String token, String cookie) {}

    private Csrf fetchCsrf() {
        ResponseEntity<Map> response = rest.getForEntity("/api/auth/csrf", Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        String cookie = cookieNamed(response.getHeaders(), "XSRF-TOKEN");
        assertThat(cookie).as("csrf endpoint must set the XSRF-TOKEN cookie").isNotNull();

        return new Csrf((String) response.getBody().get("token"), cookie);
    }

    private static String sessionCookie(HttpHeaders headers) {
        return cookieNamed(headers, "JSESSIONID");
    }

    private static String cookieNamed(HttpHeaders headers, String name) {
        List<String> cookies = headers.get(HttpHeaders.SET_COOKIE);
        if (cookies == null) {
            return null;
        }
        return cookies.stream()
                .filter(cookie -> cookie.startsWith(name + "="))
                .map(cookie -> cookie.split(";", 2)[0])
                .findFirst()
                .orElse(null);
    }
}
