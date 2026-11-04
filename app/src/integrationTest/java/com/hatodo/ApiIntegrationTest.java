package com.hatodo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

/**
 * Shared harness for tests that talk to the running app over HTTP against real
 * Postgres. Holds the cookie bookkeeping - CSRF token, then session - that every
 * authenticated call needs, so the tests themselves read as behaviour.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("itest")
@Import(TestcontainersConfiguration.class)
abstract class ApiIntegrationTest {

    /** Matches app.admin.* in application-itest.yml. */
    protected static final String ADMIN_USERNAME = "admin";
    protected static final String ADMIN_PASSWORD = "itest-admin-pw";

    @Autowired
    protected TestRestTemplate rest;

    protected record Csrf(String token, String cookie) {}

    protected Csrf fetchCsrf() {
        ResponseEntity<Map> response = rest.getForEntity("/api/auth/csrf", Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        String cookie = cookieNamed(response.getHeaders(), "XSRF-TOKEN");
        assertThat(cookie).as("csrf endpoint must set the XSRF-TOKEN cookie").isNotNull();

        return new Csrf((String) response.getBody().get("token"), cookie);
    }

    protected HttpHeaders jsonHeaders(Csrf csrf) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, csrf.cookie());
        headers.add("X-XSRF-TOKEN", csrf.token());
        headers.add(HttpHeaders.CONTENT_TYPE, "application/json");
        return headers;
    }

    protected ResponseEntity<Map> login(String username, String password) {
        Csrf csrf = fetchCsrf();
        String body = password == null
                ? "{\"username\":\"%s\"}".formatted(username)
                : "{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password);
        return rest.exchange("/api/auth/login", HttpMethod.POST,
                new HttpEntity<>(body, jsonHeaders(csrf)), Map.class);
    }

    /**
     * A session for {@code username}, carried as the cookie header every
     * subsequent call needs. The CSRF token travels with it because the token
     * lives in a cookie, not the session, so it survives the login rotation.
     */
    protected Session signIn(String username, String password) {
        Csrf csrf = fetchCsrf();
        String body = password == null
                ? "{\"username\":\"%s\"}".formatted(username)
                : "{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password);

        ResponseEntity<Map> response = rest.exchange("/api/auth/login", HttpMethod.POST,
                new HttpEntity<>(body, jsonHeaders(csrf)), Map.class);
        assertThat(response.getStatusCode()).as("sign-in as %s must succeed", username)
                .isEqualTo(HttpStatus.OK);

        String session = cookieNamed(response.getHeaders(), "JSESSIONID");
        assertThat(session).as("login must establish a session cookie").isNotNull();
        return new Session(session, csrf);
    }

    protected record Session(String cookie, Csrf csrf) {}

    protected HttpHeaders headersFor(Session session) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, session.cookie() + "; " + session.csrf().cookie());
        headers.add("X-XSRF-TOKEN", session.csrf().token());
        headers.add(HttpHeaders.CONTENT_TYPE, "application/json");
        return headers;
    }

    protected static String sessionCookie(HttpHeaders headers) {
        return cookieNamed(headers, "JSESSIONID");
    }

    protected static String cookieNamed(HttpHeaders headers, String name) {
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
