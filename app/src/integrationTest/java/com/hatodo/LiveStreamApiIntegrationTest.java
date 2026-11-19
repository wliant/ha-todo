package com.hatodo;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * The live stream against the real server, over real HTTP (0021 R179, R183).
 *
 * <p>{@link #tellsNobodyWithoutAGrant()} is why this file exists, and it has a
 * trap built into it. "Cara received nothing" is satisfied just as well by a
 * stream that never opened as by one that correctly received nothing - the same
 * two-meanings-for-one-emptiness bug this codebase has shipped before. So Cara
 * proves her connection is alive by reading a heartbeat comment first, and only
 * then is the absence of a chore meaningful. That is what
 * {@code app.live.heartbeat: PT1S} in the integration profile is for.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
class LiveStreamApiIntegrationTest extends ApiIntegrationTest {

    private static final Duration PATIENCE = Duration.ofSeconds(10);
    /** Long enough to be evidence, short enough not to pad every run. */
    private static final Duration SILENCE = Duration.ofSeconds(3);

    @LocalServerPort
    private int port;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private Listener ben;
    private Listener cara;

    @AfterEach
    void closeStreams() {
        if (ben != null) {
            ben.close();
        }
        if (cara != null) {
            cara.close();
        }
    }

    @Test
    void tellsAMemberWithAGrantThatAChoreArrived() throws Exception {
        Member ana = givenMember("ana");
        Member benMember = givenMember("ben");
        String list = newList(ana, benMember.id().toString());

        ben = listen(benMember);
        assertThat(ben.awaitHeartbeat()).as("Ben's stream must be open").isTrue();

        addItem(ana, list, "{\"description\":\"Collect the parcel\"}");

        String frame = ben.awaitData();
        assertThat(frame).as("Ben holds a grant and must be told").isNotNull();
        assertThat(frame).contains("Collect the parcel");
        assertThat(frame).contains("item.created");
    }

    @Test
    void tellsNobodyWithoutAGrant() throws Exception {
        Member ana = givenMember("ana");
        Member benMember = givenMember("ben");
        Member caraMember = givenMember("cara");
        String list = newList(ana, benMember.id().toString());

        ben = listen(benMember);
        cara = listen(caraMember);
        // Both connections proven alive BEFORE anything is created. Without
        // this, a stream that failed to open would pass the assertion below.
        assertThat(ben.awaitHeartbeat()).as("Ben's stream must be open").isTrue();
        assertThat(cara.awaitHeartbeat()).as("Cara's stream must be open").isTrue();

        addItem(ana, list, "{\"description\":\"Private chore\"}");

        assertThat(ben.awaitData()).as("Ben holds a grant").isNotNull();
        assertThat(cara.dataWithin(SILENCE))
                .as("Cara holds no grant on this list and must never hear of it")
                .isNull();
    }

    @Test
    void saysNothingAboutChoresOnListsTheMemberCannotSee() throws Exception {
        Member ana = givenMember("ana");
        Member benMember = givenMember("ben");
        String shared = newList(ana, benMember.id().toString());
        String ananOwn = newList(ana);

        ben = listen(benMember);
        assertThat(ben.awaitHeartbeat()).isTrue();

        addItem(ana, ananOwn, "{\"description\":\"Ana's own business\"}");
        addItem(ana, shared, "{\"description\":\"Something shared\"}");

        // The private one was created first, so if it were going to arrive it
        // would be the first frame.
        String frame = ben.awaitData();
        assertThat(frame).contains("Something shared");
        assertThat(frame).doesNotContain("Ana's own business");
    }

    @Test
    void theStreamIsAnEventStreamThatAskesEveryProxyNotToBufferIt() throws Exception {
        Member ana = givenMember("ana");

        ben = listen(ana);

        assertThat(ben.contentType()).startsWith("text/event-stream");
        // The only instruction in this feature that reaches the HTTPS front
        // door, which is configured outside this repository. Without it a
        // buffering proxy turns every push into a wait. See adr/0054.
        assertThat(ben.header("X-Accel-Buffering")).isEqualTo("no");
        assertThat(ben.header("Cache-Control")).contains("no-store");
    }

    @Test
    void refusesAnAnonymousListener() {
        ResponseEntity<String> response = rest.exchange("/api/live/stream", HttpMethod.GET,
                HttpEntity.EMPTY, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void signingOutClosesTheStream() throws Exception {
        Member ana = givenMember("ana");

        ben = listen(ana);
        assertThat(ben.awaitHeartbeat()).isTrue();

        rest.exchange("/api/auth/logout", HttpMethod.POST,
                new HttpEntity<>(headersFor(ana.session())), Void.class);

        assertThat(ben.awaitClosed()).as("a signed-out tab must stop receiving").isTrue();
    }

    // --- harness -------------------------------------------------------

    private record Member(UUID id, Session session) {}

    private Member givenMember(String label) {
        String username = label + "-" + UUID.randomUUID().toString().substring(0, 8);
        Session admin = signIn(ADMIN_USERNAME, ADMIN_PASSWORD);
        ResponseEntity<Map> created = rest.exchange("/api/admin/users", HttpMethod.POST,
                new HttpEntity<>("{\"username\":\"%s\",\"displayName\":\"%s\",\"password\":\"pw\"}"
                        .formatted(username, username), headersFor(admin)), Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return new Member(UUID.fromString((String) created.getBody().get("id")),
                signIn(username, "pw"));
    }

    private String newList(Member owner, String... memberIds) {
        String members = memberIds.length == 0
                ? "[]"
                : "[\"" + String.join("\",\"", memberIds) + "\"]";
        ResponseEntity<Map> created = rest.exchange("/api/lists", HttpMethod.POST,
                new HttpEntity<>("{\"name\":\"Live %s\",\"icon\":\"CHECKLIST\",\"memberIds\":%s}"
                        .formatted(UUID.randomUUID(), members), headersFor(owner.session())),
                Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (String) created.getBody().get("id");
    }

    private void addItem(Member member, String listId, String body) {
        ResponseEntity<Map> created = rest.exchange("/api/lists/" + listId + "/items",
                HttpMethod.POST, new HttpEntity<>(body, headersFor(member.session())), Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private Listener listen(Member member) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/live/stream"))
                .header("Cookie", member.session().cookie())
                .header("Accept", "text/event-stream")
                .timeout(Duration.ofMinutes(2))
                .GET()
                .build();
        return new Listener(client.send(request, HttpResponse.BodyHandlers.ofLines()));
    }

    /**
     * One open stream, drained on a thread of its own into two queues - the
     * heartbeat comments that prove it is alive, and the data frames that are
     * what the test is actually about.
     */
    private static final class Listener {

        private final HttpResponse<Stream<String>> response;
        private final BlockingQueue<String> heartbeats = new LinkedBlockingQueue<>();
        private final BlockingQueue<String> data = new LinkedBlockingQueue<>();
        private final java.util.concurrent.CountDownLatch closed =
                new java.util.concurrent.CountDownLatch(1);
        private final Thread reader;

        Listener(HttpResponse<Stream<String>> response) {
            this.response = response;
            this.reader = new Thread(this::drain, "live-listener");
            this.reader.setDaemon(true);
            this.reader.start();
        }

        private void drain() {
            try {
                response.body().forEach(line -> {
                    if (line.startsWith(":")) {
                        heartbeats.add(line);
                    } else if (line.startsWith("data:")) {
                        data.add(line);
                    }
                });
            } catch (RuntimeException ended) {
                // The server closed it, which is what signingOutClosesTheStream
                // is waiting for.
            } finally {
                closed.countDown();
            }
        }

        boolean awaitHeartbeat() throws InterruptedException {
            return heartbeats.poll(PATIENCE.toMillis(), TimeUnit.MILLISECONDS) != null;
        }

        String awaitData() throws InterruptedException {
            return data.poll(PATIENCE.toMillis(), TimeUnit.MILLISECONDS);
        }

        String dataWithin(Duration window) throws InterruptedException {
            return data.poll(window.toMillis(), TimeUnit.MILLISECONDS);
        }

        boolean awaitClosed() throws InterruptedException {
            return closed.await(PATIENCE.toMillis(), TimeUnit.MILLISECONDS);
        }

        String contentType() {
            return header("Content-Type");
        }

        String header(String name) {
            return response.headers().firstValue(name).orElse(null);
        }

        void close() {
            reader.interrupt();
        }
    }
}
