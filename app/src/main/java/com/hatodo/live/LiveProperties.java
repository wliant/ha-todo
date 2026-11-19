package com.hatodo.live;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param enabled             switches the endpoint off entirely. The client
 *                            cannot tell the resulting 404 from any other
 *                            failure to connect, so this degrades to the
 *                            fallback rather than breaking the agenda
 *                            (adr/0056).
 * @param heartbeat           how often a comment is written down every open
 *                            stream. Under nginx's 60-second
 *                            {@code proxy_read_timeout} default <em>and</em>
 *                            under the typical idle timeout of a front door we
 *                            cannot inspect. It is also the only thing that
 *                            discovers a peer that vanished - a slept phone, a
 *                            closed lid - because the write throws and the
 *                            emitter is forgotten.
 * @param streamTimeout       how long one stream lives before the client is
 *                            made to reopen it. A deliberate recycle: nothing
 *                            re-checks authorization on an emitter that is
 *                            already open, so this bounds how long a stream
 *                            authenticated at subscribe time can outlive its
 *                            session. See adr/0054.
 * @param maxStreamsPerMember so a reconnect bug in one tab cannot accumulate
 *                            emitters
 */
@ConfigurationProperties(prefix = "app.live")
public record LiveProperties(Boolean enabled, Duration heartbeat, Duration streamTimeout,
                             Integer maxStreamsPerMember) {

    /*
     * Defaulting accessors named apart from their components, as
     * RecurrenceProperties' are: a record accessor has to keep its component's
     * type.
     */

    public Duration heartbeatOrDefault() {
        return heartbeat == null ? Duration.ofSeconds(20) : heartbeat;
    }

    public Duration streamTimeoutOrDefault() {
        return streamTimeout == null ? Duration.ofMinutes(30) : streamTimeout;
    }

    public int maxStreamsOrDefault() {
        return maxStreamsPerMember == null ? 5 : maxStreamsPerMember;
    }
}
