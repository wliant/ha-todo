package com.hatodo.calendar;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The decision table, exhaustively.
 *
 * <p>This is the file whose bugs are security bugs: the app container can reach
 * an unauthenticated Docker registry on the LAN, its own actuator, Postgres and
 * the object store, and a member pastes the address this server then fetches.
 * Addresses are IP literals wherever possible so the suite needs no DNS.
 */
class FeedUrlGuardTest {

    private final FeedUrlGuard closed = new FeedUrlGuard(
            new CalendarProperties(true, null, null, null, null, false));
    private final FeedUrlGuard open = new FeedUrlGuard(
            new CalendarProperties(true, null, null, null, null, true));

    @ParameterizedTest
    @ValueSource(strings = {
            "http://93.184.216.34/basic.ics",
            "ftp://93.184.216.34/basic.ics",
            "webcal://93.184.216.34/basic.ics",
            "file:///etc/passwd",
    })
    void refusesAnythingThatIsNotHttps(String url) {
        assertThatThrownBy(() -> closed.check(URI.create(url)))
                .isInstanceOf(FeedUrlNotAllowedException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://127.0.0.1/basic.ics",
            "https://localhost/basic.ics",
            "https://10.1.2.3/basic.ics",
            "https://192.168.1.248/basic.ics",
            "https://172.16.4.4/basic.ics",
            "https://169.254.169.254/latest/meta-data",
            "https://100.100.1.1/basic.ics",
            "https://[::1]/basic.ics",
            "https://[fc00::1]/basic.ics",
            "https://0.0.0.0/basic.ics",
    })
    void refusesEveryAddressThatIsNotOnThePublicInternet(String url) {
        assertThatThrownBy(() -> closed.check(URI.create(url)))
                .isInstanceOf(FeedUrlNotAllowedException.class)
                .hasMessageContaining("not allowed");
    }

    @Test
    void refusesTheRegistryThisDeploymentActuallyRuns() {
        // 192.168.1.248:5000 is an insecure registry with no authentication,
        // reachable from the app container. This is the case adr/0035 is about.
        assertThatThrownBy(() -> closed.check(URI.create("https://192.168.1.248:5000/v2/_catalog")))
                .isInstanceOf(FeedUrlNotAllowedException.class);
    }

    @Test
    void refusesAnAddressThatNamesAPort() {
        assertThatThrownBy(() -> closed.check(URI.create("https://93.184.216.34:8080/basic.ics")))
                .isInstanceOf(FeedUrlNotAllowedException.class)
                .hasMessageContaining("port");
    }

    @Test
    void refusesAnAddressCarryingCredentials() {
        assertThatThrownBy(() -> closed.check(URI.create("https://me:secret@93.184.216.34/a.ics")))
                .isInstanceOf(FeedUrlNotAllowedException.class)
                .hasMessageContaining("username");
    }

    @Test
    void refusesSomethingWithNoHostAtAll() {
        assertThatThrownBy(() -> closed.check(URI.create("https:///basic.ics")))
                .isInstanceOf(FeedUrlNotAllowedException.class);
    }

    @Test
    void allowsAPublicHttpsAddress() {
        assertThatCode(() -> closed.check(URI.create("https://93.184.216.34/basic.ics")))
                .doesNotThrowAnyException();
        assertThatCode(() -> closed.check(URI.create("https://93.184.216.34:443/basic.ics")))
                .doesNotThrowAnyException();
    }

    @Test
    void allowsALocalAddressOnlyWhenTheDeploymentSaysSo() {
        // True locally and in the integration tests, which serve a fixture feed
        // from this machine. False in dev and prod, which is what the previous
        // tests are about.
        assertThatCode(() -> open.check(URI.create("https://127.0.0.1/basic.ics")))
                .doesNotThrowAnyException();
    }

    @Test
    void theLocalEscapeHatchRelaxesTheSchemeAndThePortWithTheHost() {
        // A fixture .ics is served over plain HTTP on an ephemeral port, so
        // relaxing the host alone would leave the feature untestable.
        assertThatCode(() -> open.check(URI.create("http://127.0.0.1:54321/basic.ics")))
                .doesNotThrowAnyException();
    }

    @Test
    void refusesANonWebSchemeEvenWithPrivateHostsAllowed() {
        // The flag is about reaching this machine, not about reading its disk.
        assertThatThrownBy(() -> open.check(URI.create("file:///etc/passwd")))
                .isInstanceOf(FeedUrlNotAllowedException.class);
    }

    @Test
    void refusesCredentialsInEveryConfiguration() {
        assertThatThrownBy(() -> open.check(URI.create("http://me:secret@127.0.0.1/a.ics")))
                .isInstanceOf(FeedUrlNotAllowedException.class);
    }
}
