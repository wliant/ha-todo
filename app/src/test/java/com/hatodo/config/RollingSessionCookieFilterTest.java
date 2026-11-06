package com.hatodo.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.server.servlet.Session;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * The rolling cookie (docs/adr/0038).
 *
 * <p>What is being prevented: Tomcat writes the session cookie once, at session
 * creation, so a thirty-day Max-Age counts from sign-in. A member who opens the
 * app every morning would be signed out on day thirty with nothing to explain
 * it.
 */
class RollingSessionCookieFilterTest {

    private static final Instant NOON = Instant.parse("2026-11-06T12:00:00Z");

    private Session sessionProperties(boolean secure, Duration maxAge) {
        Session session = new Session();
        session.setTimeout(Duration.ofDays(30));
        session.getCookie().setName("JSESSIONID");
        session.getCookie().setHttpOnly(true);
        session.getCookie().setSecure(secure);
        session.getCookie().setMaxAge(maxAge);
        session.getCookie().setSameSite(org.springframework.boot.web.server.Cookie.SameSite.LAX);
        return session;
    }

    private MockHttpServletResponse through(RollingSessionCookieFilter filter,
                                            MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test
    void doesNotTouchAnythingForARequestWithNoSession() throws Exception {
        // An anonymous request that minted a session would be a session per bot.
        RollingSessionCookieFilter filter = new RollingSessionCookieFilter(
                sessionProperties(true, Duration.ofDays(30)), Duration.ofDays(1),
                Clock.fixed(NOON, ZoneId.of("UTC")));

        MockHttpServletResponse response = through(filter, new MockHttpServletRequest());

        assertThat(response.getCookies()).isEmpty();
    }

    @Test
    void leavesAFreshSessionAloneRatherThanRewritingACookieItJustGot() throws Exception {
        RollingSessionCookieFilter filter = new RollingSessionCookieFilter(
                sessionProperties(true, Duration.ofDays(30)), Duration.ofDays(1),
                Clock.fixed(NOON, ZoneId.of("UTC")));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession(true);

        assertThat(through(filter, request).getCookies()).isEmpty();
    }

    @Test
    void reissuesTheCookieOnceTheIntervalHasPassed() throws Exception {
        RollingSessionCookieFilter filter = new RollingSessionCookieFilter(
                sessionProperties(true, Duration.ofDays(30)), Duration.ofDays(1),
                Clock.fixed(NOON, ZoneId.of("UTC")));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession(true).setAttribute(RollingSessionCookieFilter.REFRESHED_AT,
                NOON.minus(Duration.ofDays(2)));

        Cookie[] cookies = through(filter, request).getCookies();

        assertThat(cookies).hasSize(1);
        assertThat(cookies[0].getName()).isEqualTo("JSESSIONID");
        assertThat(cookies[0].getValue()).isEqualTo(request.getSession().getId());
        assertThat(cookies[0].getMaxAge()).isEqualTo((int) Duration.ofDays(30).toSeconds());
    }

    @Test
    void carriesEveryAttributeTheContainerWouldHaveWritten() throws Exception {
        // Dropping one here - Secure above all - would downgrade the cookie on
        // the next refresh rather than at sign-in, a month after the change.
        RollingSessionCookieFilter filter = new RollingSessionCookieFilter(
                sessionProperties(true, Duration.ofDays(30)), Duration.ofDays(1),
                Clock.fixed(NOON, ZoneId.of("UTC")));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession(true).setAttribute(RollingSessionCookieFilter.REFRESHED_AT,
                NOON.minus(Duration.ofDays(2)));

        Cookie cookie = through(filter, request).getCookies()[0];

        assertThat(cookie.getSecure()).isTrue();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getPath()).isEqualTo("/");
        assertThat(cookie.getAttribute("SameSite")).isEqualTo("Lax");
    }

    @Test
    void refreshesAtMostOncePerInterval() throws Exception {
        RollingSessionCookieFilter filter = new RollingSessionCookieFilter(
                sessionProperties(true, Duration.ofDays(30)), Duration.ofDays(1),
                Clock.fixed(NOON, ZoneId.of("UTC")));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession(true).setAttribute(RollingSessionCookieFilter.REFRESHED_AT,
                NOON.minus(Duration.ofDays(2)));

        assertThat(through(filter, request).getCookies()).hasSize(1);
        // The dashboard makes several requests per visit; only the first moves
        // the expiry.
        assertThat(through(filter, request).getCookies()).isEmpty();
    }

    @Test
    void fallsBackToTheSessionTimeoutWhenNoCookieLifetimeIsConfigured() throws Exception {
        // So the two can never drift: an unset max-age means "as long as the
        // session lasts", not some invented number.
        RollingSessionCookieFilter filter = new RollingSessionCookieFilter(
                sessionProperties(true, null), Duration.ofDays(1),
                Clock.fixed(NOON, ZoneId.of("UTC")));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession(true).setAttribute(RollingSessionCookieFilter.REFRESHED_AT,
                NOON.minus(Duration.ofDays(2)));

        assertThat(through(filter, request).getCookies()[0].getMaxAge())
                .isEqualTo((int) Duration.ofDays(30).toSeconds());
    }

    @Test
    void doesNotMarkTheCookieSecureWhenTheDeploymentIsNot() throws Exception {
        // Local development is plain http; a Secure cookie there is a session
        // that never arrives.
        RollingSessionCookieFilter filter = new RollingSessionCookieFilter(
                sessionProperties(false, Duration.ofDays(30)), Duration.ofDays(1),
                Clock.fixed(NOON, ZoneId.of("UTC")));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession(true).setAttribute(RollingSessionCookieFilter.REFRESHED_AT,
                NOON.minus(Duration.ofDays(2)));

        assertThat(through(filter, request).getCookies()[0].getSecure()).isFalse();
    }
}
