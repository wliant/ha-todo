package com.hatodo.live;

import com.hatodo.user.CurrentUserProvider;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * The one long-lived connection in the product (0021 R179).
 *
 * <p><strong>It takes no parameters at all</strong> - no list id, no scope. The
 * subscriber's identity is the session's, exactly as {@code /api/dashboard/*}
 * takes no list id, and who may hear about a given chore is decided per event
 * rather than per subscription. There is therefore no channel to subscribe to
 * by mistake. See adr/0055.
 *
 * <p>The condition is this feature's kill switch, and it sits on the component
 * itself as {@code CalendarSyncJob}'s does - {@code @SpringBootApplication}
 * scans this package, so a conditional {@code @Configuration} would not keep a
 * {@code @RestController} out of the context. Switched off, the endpoint is
 * absent and answers 404, which the client cannot distinguish from any other
 * failure to connect: it falls back to refreshing periodically rather than
 * breaking the agenda (adr/0056).
 */
@ConditionalOnProperty(name = "app.live.enabled", havingValue = "true", matchIfMissing = true)
@RestController
@RequestMapping("/api/live")
public class LiveController {

    private final LiveBroker broker;
    private final CurrentUserProvider currentUser;

    public LiveController(LiveBroker broker, CurrentUserProvider currentUser) {
        this.broker = broker;
        this.currentUser = currentUser;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(HttpServletResponse response) {
        // The single most valuable line in this feature. Our own nginx is told
        // not to buffer by its location block, but the HTTPS front door in
        // front of both environments is configured outside this repository -
        // and every nginx-derived proxy honours this header on a response. A
        // buffered stream is a feature that works perfectly in development and
        // never once pushes in production. See adr/0054.
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        return broker.subscribe(currentUser.requireUserId());
    }
}
