package com.hatodo.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.server.Cookie.SameSite;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;
import org.springframework.boot.web.server.servlet.Session;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Keeps the session cookie's expiry moving while the member keeps using the app
 * (docs/adr/0038).
 *
 * <p>Tomcat writes the session cookie <b>once</b>, when the session is created,
 * and never sends it again. So a thirty-day {@code Max-Age} expires thirty days
 * after signing in rather than thirty days after the last visit, and somebody
 * who opens the app every morning would still be signed out on day thirty with
 * no explanation. This re-sends the cookie so the browser pushes its expiry
 * out.
 *
 * <p>At most once a day per session, tracked in a session attribute. A
 * {@code Set-Cookie} on every response is a header on every request the
 * dashboard makes and a cookie-jar write on every one, to move a date that
 * needs moving once.
 *
 * <p>Nothing here authenticates anybody: it re-issues a cookie the browser
 * already holds for a session the container already accepted. It deliberately
 * does not create a session - {@code getSession(false)} - because an anonymous
 * request that minted one would be a session per bot.
 */
@Component
public class RollingSessionCookieFilter extends OncePerRequestFilter {

    /**
     * Visible for the test, which has no patience for a day.
     */
    static final String REFRESHED_AT = RollingSessionCookieFilter.class.getName() + ".refreshedAt";

    private final Session session;
    private final Duration refreshEvery;
    private final Clock clock;

    @Autowired
    public RollingSessionCookieFilter(ServerProperties serverProperties) {
        this(serverProperties.getServlet().getSession(), Duration.ofDays(1), Clock.systemUTC());
    }

    /** For the test, which has no patience for a day. */
    RollingSessionCookieFilter(Session session, Duration refreshEvery, Clock clock) {
        this.session = session;
        this.refreshEvery = refreshEvery;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        chain.doFilter(request, response);

        HttpSession existing = request.getSession(false);
        if (existing == null || !isDue(existing)) {
            return;
        }
        reissue(request, response, existing.getId());
        existing.setAttribute(REFRESHED_AT, clock.instant());
    }

    private boolean isDue(HttpSession existing) {
        Object last = existing.getAttribute(REFRESHED_AT);
        if (!(last instanceof Instant refreshedAt)) {
            // Set on the first request of a session rather than left null, so
            // the cookie is only re-sent once the interval has actually passed.
            existing.setAttribute(REFRESHED_AT, clock.instant());
            return false;
        }
        return refreshedAt.plus(refreshEvery).isBefore(clock.instant());
    }

    /**
     * The same cookie, with its clock restarted.
     *
     * <p>Every attribute is taken from the same configuration Tomcat used to
     * write it in the first place. Dropping one here - {@code Secure} above all
     * - would quietly downgrade the cookie on the next refresh rather than at
     * sign-in, which is the kind of bug that surfaces a month later.
     */
    private void reissue(HttpServletRequest request, HttpServletResponse response, String id) {
        Cookie cookie = new Cookie(name(), id);
        cookie.setPath(request.getContextPath().isEmpty() ? "/" : request.getContextPath());
        cookie.setHttpOnly(Boolean.TRUE.equals(session.getCookie().getHttpOnly()));
        cookie.setSecure(Boolean.TRUE.equals(session.getCookie().getSecure()));
        cookie.setMaxAge((int) maxAge().toSeconds());
        SameSite sameSite = session.getCookie().getSameSite();
        if (sameSite != null && sameSite.attributeValue() != null) {
            cookie.setAttribute("SameSite", sameSite.attributeValue());
        }
        response.addCookie(cookie);
    }

    private String name() {
        String configured = session.getCookie().getName();
        return configured == null || configured.isBlank() ? "JSESSIONID" : configured;
    }

    /**
     * The configured cookie lifetime, or the session's own if none is set -
     * never an invented number, so the two cannot drift apart.
     */
    private Duration maxAge() {
        Duration configured = session.getCookie().getMaxAge();
        return configured != null ? configured : session.getTimeout();
    }
}
