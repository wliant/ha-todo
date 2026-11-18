package com.hatodo;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The household's day, proved rather than described (requirements/0019 R166).
 *
 * <p>The integration context fixes {@code app.timezone: Asia/Singapore} while
 * the JVM and the container run wherever they run - which in a deployment is
 * UTC. Every assertion below would still pass if the code read
 * {@code LocalDate.now()} with no zone <em>on a machine in Singapore</em>, and
 * would fail on the server. So each one names Singapore explicitly rather than
 * comparing against the default clock.
 *
 * <p>This is the file to run first when a day lands on the wrong side of
 * midnight.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
class TrackerTimezoneIntegrationTest extends ApiIntegrationTest {

    private static final ZoneId HOUSEHOLD = ZoneId.of("Asia/Singapore");

    @Autowired
    private JdbcTemplate jdbc;

    private record Member(UUID id, Session session) {}

    private Member givenMember(String label) {
        String username = label + "-" + UUID.randomUUID().toString().substring(0, 8);
        Session admin = signIn(ADMIN_USERNAME, ADMIN_PASSWORD);
        ResponseEntity<Map> created = rest.exchange("/api/admin/users", HttpMethod.POST,
                new HttpEntity<>("{\"username\":\"%s\",\"displayName\":\"%s\",\"password\":\"pw-%s\"}"
                        .formatted(username, username, username), headersFor(admin)), Map.class);
        return new Member(UUID.fromString((String) created.getBody().get("id")),
                signIn(username, "pw-" + username));
    }

    private String tracker(Member owner, String name, String period, String scope) {
        return (String) rest.exchange("/api/trackers", HttpMethod.POST, new HttpEntity<>(
                ("{\"name\":\"%s\",\"icon\":\"HEALTH\",\"unit\":\"kg\",\"period\":\"%s\","
                        + "\"scope\":\"%s\",\"rollup\":\"LATEST\",\"memberIds\":[]}")
                        .formatted(name, period, scope),
                headersFor(owner.session())), Map.class).getBody().get("id");
    }

    /**
     * The nudge asks about the household's today, not the container's.
     *
     * <p>Between 00:00 and 08:00 Singapore time the two differ by a day, which
     * is exactly when a UTC-based implementation would ask about yesterday and
     * nag a member who has already recorded.
     */
    @Test
    void theNudgeAsksAboutTheHouseholdsToday() {
        Member ana = givenMember("tz-pending");
        String id = tracker(ana, "Weight " + UUID.randomUUID(), "DAY", "PER_MEMBER");

        ResponseEntity<List> pending = rest.exchange("/api/trackers/pending", HttpMethod.GET,
                new HttpEntity<>(headersFor(ana.session())), List.class);

        Map<String, Object> row = ((List<Map<String, Object>>) pending.getBody()).stream()
                .filter(entry -> id.equals(entry.get("id")))
                .findFirst().orElseThrow();
        assertThat(row.get("periodStart")).isEqualTo(LocalDate.now(HOUSEHOLD).toString());
    }

    /**
     * And "opened this" buckets on the household's day too - the same rule
     * {@code occurs_on} follows, applied to the interaction buckets the sidebar
     * orders by (adr/0041).
     */
    @Test
    void openingATrackerCountsAgainstTheHouseholdsDay() {
        Member ana = givenMember("tz-touch");
        String id = tracker(ana, "Weight " + UUID.randomUUID(), "DAY", "PER_MEMBER");

        rest.exchange("/api/trackers/" + id + "/touch", HttpMethod.POST,
                new HttpEntity<>(headersFor(ana.session())), Void.class);

        String onDay = jdbc.queryForObject(
                "SELECT on_day::text FROM tracker_interaction_day WHERE tracker_id = ?::uuid",
                String.class, id);
        assertThat(onDay).isEqualTo(LocalDate.now(HOUSEHOLD).toString());
    }

    /**
     * A reading recorded at the very end of a month lands in that month.
     *
     * <p>The canonicalisation runs on the date the form sent rather than on a
     * clock, which is what makes this true regardless of where the server is -
     * and the assertion reads the stored column rather than the response, so a
     * canonicalisation that happened only on the way out would fail here.
     */
    @Test
    void theLastDayOfAMonthBucketsIntoThatMonth() {
        Member ana = givenMember("tz-month");
        String id = tracker(ana, "Spend " + UUID.randomUUID(), "MONTH", "SHARED");

        rest.exchange("/api/trackers/" + id + "/readings", HttpMethod.POST,
                new HttpEntity<>("{\"periodStart\":\"2026-11-30\",\"amount\":980}",
                        headersFor(ana.session())), Map.class);

        String stored = jdbc.queryForObject(
                "SELECT period_start::text FROM tracker_reading WHERE tracker_id = ?::uuid",
                String.class, id);
        assertThat(stored).isEqualTo("2026-11-01");
    }

    /**
     * The series window's default end is the household's today, which is what
     * keeps the right-hand edge of the chart from being tomorrow or yesterday
     * depending on the hour.
     */
    @Test
    void theDefaultSeriesWindowEndsOnTheHouseholdsToday() {
        Member ana = givenMember("tz-series");
        String id = tracker(ana, "Weight " + UUID.randomUUID(), "DAY", "PER_MEMBER");

        ResponseEntity<Map> series = rest.exchange("/api/trackers/" + id + "/series",
                HttpMethod.GET, new HttpEntity<>(headersFor(ana.session())), Map.class);

        List<String> buckets = (List<String>) series.getBody().get("buckets");
        assertThat(buckets).isNotEmpty();
        assertThat(buckets.get(buckets.size() - 1))
                .isEqualTo(LocalDate.now(HOUSEHOLD).toString());
    }
}
