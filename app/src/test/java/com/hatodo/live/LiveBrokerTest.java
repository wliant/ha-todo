package com.hatodo.live;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * The registry, and the rules that keep it honest.
 *
 * <p>The one that matters most is {@link #writesOnlyToTheMembersNamed()}: a
 * member is only ever written to when they were named. Broadcasting is cheaper
 * and would put every private list's chore descriptions into every connected
 * browser, while passing every other test here - see adr/0055.
 */
class LiveBrokerTest {

    private static final UUID ANA = UUID.randomUUID();
    private static final UUID BEN = UUID.randomUUID();
    private static final Duration PATIENCE = Duration.ofSeconds(2);

    private LiveBroker broker;

    @BeforeEach
    void start() {
        // A heartbeat far enough out that it cannot interleave with an
        // assertion about delivery. LiveStreamApiIntegrationTest is where a
        // fast one is actually wanted.
        broker = brokerWith(5);
    }

    @AfterEach
    void stop() {
        broker.stop();
    }

    @Test
    void registersAStreamForTheMemberWhoOpenedIt() {
        broker.subscribe(ANA);

        assertThat(broker.streamCount(ANA)).isEqualTo(1);
        assertThat(broker.streamCount(BEN)).isZero();
    }

    @Test
    void writesOnlyToTheMembersNamed() throws InterruptedException {
        Watching ana = watch(ANA);
        Watching ben = watch(BEN);

        broker.send(Map.of(ANA, notification("Milk")));

        assertThat(ana.awaitOne()).isTrue();
        // Ben's stream is open and was not written to. Had the broker
        // broadcast, this is the assertion that would fail.
        assertThat(ben.received()).isEmpty();
    }

    @Test
    void givesEachRecipientTheirOwnPayload() throws InterruptedException {
        Watching ana = watch(ANA);
        Watching ben = watch(BEN);

        broker.send(Map.of(ANA, notification("Milk"), BEN, notification("Bins")));

        assertThat(ana.awaitOne()).isTrue();
        assertThat(ben.awaitOne()).isTrue();
    }

    /*
     * Deregistration is driven through the callbacks the broker wired, not
     * through emitter.complete(): an SseEmitter that Spring MVC never
     * initialised has no handler, so complete() flips a flag and returns
     * without running anything. Capturing the callback and running it is what
     * actually asserts the broker cleans up after itself.
     */

    @Test
    void forgetsAStreamThatCompletes() {
        Watching emitter = watch(ANA);

        emitter.completion().run();

        assertThat(broker.streamCount(ANA)).isZero();
    }

    @Test
    void forgetsAStreamThatTimesOut() {
        Watching emitter = watch(ANA);

        emitter.timeout().run();

        assertThat(broker.streamCount(ANA)).isZero();
    }

    @Test
    void forgettingTheSameStreamTwiceIsHarmless() {
        Watching emitter = watch(ANA);

        emitter.completion().run();
        emitter.completion().run();

        assertThat(broker.streamCount(ANA)).isZero();
    }

    @Test
    void forgetsAStreamWhoseWriteFails() throws InterruptedException {
        Failing gone = new Failing();
        broker.register(ANA, gone);

        broker.send(Map.of(ANA, notification("Milk")));

        assertThat(gone.awaitAttempt()).isTrue();
        eventually(() -> broker.streamCount(ANA) == 0);
    }

    @Test
    void sendingToAMemberWithNoStreamIsSilent() {
        broker.send(Map.of(ANA, notification("Milk")));

        assertThat(broker.streamCount(ANA)).isZero();
    }

    @Test
    void capsHowManyStreamsOneMemberCanHold() {
        LiveBroker capped = brokerWith(2);
        try {
            capped.subscribe(ANA);
            capped.subscribe(ANA);
            capped.subscribe(ANA);
            capped.subscribe(ANA);

            assertThat(capped.streamCount(ANA)).isEqualTo(2);
        } finally {
            capped.stop();
        }
    }

    @Test
    void signingOutClosesEverythingThatMemberHeld() {
        broker.subscribe(ANA);
        broker.subscribe(ANA);
        broker.subscribe(BEN);

        broker.disconnect(ANA);

        assertThat(broker.streamCount(ANA)).isZero();
        assertThat(broker.streamCount(BEN)).isEqualTo(1);
    }

    @Test
    void aHeartbeatForgetsAStreamWhosePeerHasGone() throws InterruptedException {
        // The heartbeat is the only thing that notices a slept phone or a
        // closed lid. Without it, every closed tab leaks an emitter until its
        // timeout.
        LiveBroker beating = new LiveBroker(new LiveProperties(true, Duration.ofMillis(20),
                Duration.ofMinutes(30), 5));
        beating.start();
        try {
            Failing gone = new Failing();
            beating.register(ANA, gone);

            assertThat(gone.awaitAttempt()).isTrue();
            eventually(() -> beating.streamCount(ANA) == 0);
        } finally {
            beating.stop();
        }
    }

    /**
     * The latch a failing emitter counts down fires <em>before</em> it throws,
     * so the broker's cleanup is still ahead of us on the writer thread.
     */
    private void eventually(java.util.function.BooleanSupplier condition) {
        long deadline = System.nanoTime() + PATIENCE.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.onSpinWait();
        }
        assertThat(condition.getAsBoolean()).as("condition never held").isTrue();
    }

    private LiveBroker brokerWith(int maxStreams) {
        LiveBroker started = new LiveBroker(new LiveProperties(true, Duration.ofHours(1),
                Duration.ofMinutes(30), maxStreams));
        started.start();
        return started;
    }

    private LiveNotification notification(String description) {
        return new LiveNotification(LiveNotification.ITEM_CREATED, UUID.randomUUID(),
                UUID.randomUUID(), "Groceries", description, "Ben", false);
    }

    private Watching watch(UUID userId) {
        Watching emitter = new Watching();
        broker.register(userId, emitter);
        return emitter;
    }

    /** An emitter that records what the broker wrote to it. */
    private static final class Watching extends SseEmitter {
        private final List<Object> received = new ArrayList<>();
        private final CountDownLatch one = new CountDownLatch(1);
        private Runnable completion;
        private Runnable timeout;

        @Override
        public synchronized void send(SseEventBuilder builder) {
            received.add(builder);
            one.countDown();
        }

        @Override
        public void onCompletion(Runnable callback) {
            this.completion = callback;
        }

        @Override
        public void onTimeout(Runnable callback) {
            this.timeout = callback;
        }

        Runnable completion() {
            return completion;
        }

        Runnable timeout() {
            return timeout;
        }

        boolean awaitOne() throws InterruptedException {
            return one.await(PATIENCE.toMillis(), TimeUnit.MILLISECONDS);
        }

        synchronized List<Object> received() {
            return List.copyOf(received);
        }
    }

    /** A peer that went away between the last heartbeat and now. */
    private static final class Failing extends SseEmitter {
        private final CountDownLatch attempted = new CountDownLatch(1);

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            attempted.countDown();
            throw new IOException("peer went away");
        }

        boolean awaitAttempt() throws InterruptedException {
            return attempted.await(PATIENCE.toMillis(), TimeUnit.MILLISECONDS);
        }
    }
}
