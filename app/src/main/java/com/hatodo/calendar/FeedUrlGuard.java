package com.hatodo.calendar;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import org.springframework.stereotype.Component;

/**
 * The only place a feed address is approved (docs/adr/0035).
 *
 * <p>A member pastes the URL this server then fetches, so the destination is
 * typo-chosen in the common case and attacker-chosen in the general one - and
 * this deployment makes the general case concrete. The app container can reach
 * a Docker registry on {@code 192.168.9.9:5000} over plain HTTP with no
 * authentication, its own {@code app:8080/actuator}, Postgres, and the object
 * store. A feed pointed at any of those turns "add a calendar" into a
 * request-forgery primitive whose response the member reads in the preview that
 * R65 requires.
 *
 * <p>Small on purpose: this is the file whose bugs are security bugs, so it
 * should fit on a screen and its decision table is a test.
 */
@Component
public class FeedUrlGuard {

    private final CalendarProperties properties;

    public FeedUrlGuard(CalendarProperties properties) {
        this.properties = properties;
    }

    /**
     * @throws FeedUrlNotAllowedException if this server must not fetch it
     */
    public void check(URI url) {
        if (url.getHost() == null || url.getHost().isBlank()) {
            throw new FeedUrlNotAllowedException("That does not look like a web address.");
        }
        if (url.getUserInfo() != null) {
            // user:password@host is how a confusing authority is smuggled past
            // a reader, and no feed needs one. Refused in every configuration.
            throw new FeedUrlNotAllowedException("A calendar address cannot contain a username.");
        }
        if (properties.privateHostsAllowed()) {
            // Local and test only, and it relaxes the scheme and the port with
            // the host - a fixture .ics is served over plain HTTP on an
            // ephemeral port, so relaxing the host alone would leave the
            // feature untestable, which is the failure this flag exists to
            // avoid. Never true in a deployment; adr/0035 says why.
            requireWebScheme(url);
            return;
        }
        if (url.getScheme() == null || !url.getScheme().equalsIgnoreCase("https")) {
            // Not a nicety: both providers publish over HTTPS, and plain HTTP
            // would put a calendar's bearer URL on the wire in clear.
            throw new FeedUrlNotAllowedException("A calendar address has to start with https://");
        }
        if (url.getPort() != -1 && url.getPort() != 443) {
            throw new FeedUrlNotAllowedException(
                    "A calendar address cannot name a port. Use the address the calendar gave you.");
        }
        requirePublic(url.getHost());
    }

    private void requireWebScheme(URI url) {
        String scheme = url.getScheme() == null ? "" : url.getScheme().toLowerCase();
        if (!scheme.equals("https") && !scheme.equals("http")) {
            throw new FeedUrlNotAllowedException("A calendar address has to start with https://");
        }
    }

    /**
     * Every address the host resolves to has to be on the public internet.
     *
     * <p>Every address, not the first: a hostname with both a public and a
     * private record is the obvious way past a check that stops at one.
     */
    private void requirePublic(String host) {
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException unknown) {
            throw new FeedUrlNotAllowedException("That address could not be found.");
        }
        for (InetAddress address : addresses) {
            if (isPrivate(address)) {
                throw new FeedUrlNotAllowedException(
                        "That address is not allowed. A calendar feed has to be on the internet.");
            }
        }
    }

    private boolean isPrivate(InetAddress address) {
        if (address.isLoopbackAddress() || address.isAnyLocalAddress()
                || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        byte[] octets = address.getAddress();
        if (octets.length == 4) {
            int first = octets[0] & 0xff;
            int second = octets[1] & 0xff;
            // 100.64.0.0/10, carrier-grade NAT: not "site local" to Java, and
            // routable to a provider's own infrastructure.
            if (first == 100 && second >= 64 && second <= 127) {
                return true;
            }
            // 192.0.0.0/24 and 198.18.0.0/15 are infrastructure and benchmark
            // ranges respectively; neither hosts a household's calendar.
            if (first == 192 && second == 0 && (octets[2] & 0xff) == 0) {
                return true;
            }
            return first == 198 && (second == 18 || second == 19);
        }
        // fc00::/7, IPv6 unique-local. isSiteLocalAddress() is fec0::/10, the
        // deprecated range, and answers false for the one actually in use.
        return octets.length == 16 && (octets[0] & 0xfe) == 0xfc;
    }
}
