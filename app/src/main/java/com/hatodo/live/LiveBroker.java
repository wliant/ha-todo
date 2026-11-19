package com.hatodo.live;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Who is listening, and the one thread that writes to them.
 *
 * <p>Keyed by member, never by list: there is no list-addressable channel to
 * subscribe to by mistake, and who may hear about a chore is decided per event
 * by {@link ItemCreatedListener} rather than cached on a connection. See
 * adr/0055.
 */
@Component
public class LiveBroker {

    private static final Logger log = LoggerFactory.getLogger(LiveBroker.class);

    private final Map<UUID, Set<SseEmitter>> streams = new ConcurrentHashMap<>();
    private final LiveProperties properties;
    private ScheduledExecutorService writer;

    public LiveBroker(LiveProperties properties) {
        this.properties = properties;
    }

    /**
     * <strong>One thread writes, always.</strong> {@link SseEmitter} is not
     * safe for concurrent sends: a heartbeat and an event written at once can
     * interleave mid-frame, and a corrupted frame is simply a dropped event -
     * no exception on either side, nothing logged, nothing failing. Making a
     * single thread the only writer removes that by construction rather than by
     * remembering to synchronise.
     *
     * <p>It also keeps the fan-out off the request thread of the member who
     * just pressed Add, whose button would otherwise wait on somebody else's
     * stalled socket.
     */
    @PostConstruct
    void start() {
        writer = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "live-writer");
            thread.setDaemon(true);
            return thread;
        });
        long millis = properties.heartbeatOrDefault().toMillis();
        writer.scheduleAtFixedRate(this::heartbeat, millis, millis, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    void stop() {
        if (writer != null) {
            writer.shutdownNow();
        }
        streams.values().forEach(set -> set.forEach(SseEmitter::complete));
        streams.clear();
    }

    /** A new listener. Never returns null; the caller returns it to Spring MVC. */
    public SseEmitter subscribe(UUID userId) {
        return register(userId, new SseEmitter(properties.streamTimeoutOrDefault().toMillis()));
    }

    /**
     * Visible for testing, which is the only reason the emitter is a parameter:
     * a test that wants to watch what was written needs to supply its own.
     */
    SseEmitter register(UUID userId, SseEmitter emitter) {
        // All three, and all idempotent. onTimeout additionally completes:
        // Spring does not do it for you, and an uncompleted timed-out emitter
        // logs an AsyncRequestTimeoutException and stays in the map.
        emitter.onCompletion(() -> forget(userId, emitter));
        emitter.onError(error -> forget(userId, emitter));
        emitter.onTimeout(() -> {
            forget(userId, emitter);
            emitter.complete();
        });

        streams.compute(userId, (key, existing) -> {
            Set<SseEmitter> set = existing == null ? ConcurrentHashMap.newKeySet() : existing;
            set.add(emitter);
            return set;
        });
        capStreams(userId);
        return emitter;
    }

    /**
     * Writes one notification to each named member, on the writer thread.
     *
     * <p>Each recipient gets their own payload, because {@code byYou} is a
     * property of the recipient rather than of the event.
     */
    public void send(Map<UUID, LiveNotification> perRecipient) {
        if (perRecipient.isEmpty()) {
            return;
        }
        writer.execute(() -> perRecipient.forEach(this::deliver));
    }

    /** Closes everything this member holds - they signed out. */
    public void disconnect(UUID userId) {
        Set<SseEmitter> theirs = streams.remove(userId);
        if (theirs != null) {
            theirs.forEach(SseEmitter::complete);
        }
    }

    /** How many streams this member holds. For tests and for the cap. */
    int streamCount(UUID userId) {
        Set<SseEmitter> theirs = streams.get(userId);
        return theirs == null ? 0 : theirs.size();
    }

    private void deliver(UUID userId, LiveNotification notification) {
        for (SseEmitter emitter : emittersFor(userId)) {
            try {
                emitter.send(SseEmitter.event()
                        .name(notification.type())
                        .data(notification));
            } catch (IOException | IllegalStateException gone) {
                // The peer went away between the last heartbeat and now. Not
                // worth a stack trace: it is the ordinary end of a stream.
                forget(userId, emitter);
            }
        }
    }

    private void heartbeat() {
        streams.forEach((userId, theirs) -> {
            for (SseEmitter emitter : List.copyOf(theirs)) {
                try {
                    // A comment, so no client listener is needed for it.
                    emitter.send(SseEmitter.event().comment("hb"));
                } catch (IOException | IllegalStateException gone) {
                    forget(userId, emitter);
                }
            }
        });
    }

    private Collection<SseEmitter> emittersFor(UUID userId) {
        Set<SseEmitter> theirs = streams.get(userId);
        return theirs == null ? List.of() : List.copyOf(theirs);
    }

    /**
     * <strong>Through {@code compute}, never {@code get().remove()} followed by
     * {@code if (isEmpty()) map.remove()}.</strong> That pair races: one thread
     * finds the set empty and drops the key while another is adding an emitter
     * to the set it has just orphaned. The symptom is a member who is
     * connected, is heartbeating, and never receives anything - with nothing
     * logged anywhere.
     */
    private void forget(UUID userId, SseEmitter emitter) {
        streams.computeIfPresent(userId, (key, existing) -> {
            existing.remove(emitter);
            return existing.isEmpty() ? null : existing;
        });
    }

    private void capStreams(UUID userId) {
        int max = properties.maxStreamsOrDefault();
        Set<SseEmitter> theirs = streams.get(userId);
        if (theirs == null || theirs.size() <= max) {
            return;
        }
        // Oldest first. A set has no order, so this is approximate by
        // construction - the point is a bound, not a policy.
        List<SseEmitter> surplus = new ArrayList<>(theirs);
        for (int i = 0; i < surplus.size() - max; i++) {
            SseEmitter doomed = surplus.get(i);
            forget(userId, doomed);
            doomed.complete();
        }
        log.debug("capped live streams for {}", userId);
    }
}
