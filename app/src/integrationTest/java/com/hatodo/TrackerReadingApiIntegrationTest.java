package com.hatodo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The numbers, against real Postgres (requirements/0019 R162, R163, R165).
 *
 * <p>Two of these exist because the invariants they check are
 * <strong>service-enforced rather than constrained</strong>, and both fail
 * silently: a stray member id on a shared tracker is invisible to every read,
 * and an uncanonical period start is a second November that no error reports.
 * Asserting them against the real column is the {@code setweight} lesson
 * (adr/0033) - probe the database rather than trusting the description.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
class TrackerReadingApiIntegrationTest extends ApiIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    private record Member(String username, UUID id, Session session) {}

    private Member givenMember(String label) {
        String username = label + "-" + UUID.randomUUID().toString().substring(0, 8);
        Session admin = signIn(ADMIN_USERNAME, ADMIN_PASSWORD);
        ResponseEntity<Map> created = rest.exchange("/api/admin/users", HttpMethod.POST,
                new HttpEntity<>("{\"username\":\"%s\",\"displayName\":\"%s\",\"password\":\"pw-%s\"}"
                        .formatted(username, username, username), headersFor(admin)), Map.class);
        UUID id = UUID.fromString((String) created.getBody().get("id"));
        return new Member(username, id, signIn(username, "pw-" + username));
    }

    private String tracker(Member owner, String name, String period, String scope, String rollup,
                           String... memberIds) {
        String members = memberIds.length == 0
                ? "[]"
                : "[\"" + String.join("\",\"", memberIds) + "\"]";
        return (String) rest.exchange("/api/trackers", HttpMethod.POST, new HttpEntity<>(
                ("{\"name\":\"%s\",\"icon\":\"HEALTH\",\"unit\":\"kg\",\"period\":\"%s\","
                        + "\"scope\":\"%s\",\"rollup\":\"%s\",\"memberIds\":%s}")
                        .formatted(name, period, scope, rollup, members),
                headersFor(owner.session())), Map.class).getBody().get("id");
    }

    private ResponseEntity<Map> record(Session session, String trackerId, String day,
                                       String amount) {
        return rest.exchange("/api/trackers/" + trackerId + "/readings", HttpMethod.POST,
                new HttpEntity<>("{\"periodStart\":\"%s\",\"amount\":%s}".formatted(day, amount),
                        headersFor(session)), Map.class);
    }

    /**
     * The canonicalisation invariant, read back from the column rather than
     * from the response: a monthly tracker sent the 18th must store the 1st, or
     * November exists twice and every fold treats the two as different buckets.
     */
    @Test
    void aMonthlyReadingIsStoredAtTheFirstWhateverDayWasSent() {
        Member ana = givenMember("rd-bucket");
        String id = tracker(ana, "Spend " + ana.username(), "MONTH", "SHARED", "SUM");

        record(ana.session(), id, "2026-11-18", "142");

        String stored = jdbc.queryForObject(
                "SELECT period_start::text FROM tracker_reading WHERE tracker_id = ?::uuid",
                String.class, id);
        assertThat(stored).isEqualTo("2026-11-01");
    }

    @Test
    void aWeeklyReadingIsStoredAtItsMonday() {
        Member ana = givenMember("rd-week");
        String id = tracker(ana, "Reading " + ana.username(), "WEEK", "PER_MEMBER", "SUM");

        // 2026-11-22 is a Sunday; its week began on the 16th.
        record(ana.session(), id, "2026-11-22", "90");

        String stored = jdbc.queryForObject(
                "SELECT period_start::text FROM tracker_reading WHERE tracker_id = ?::uuid",
                String.class, id);
        assertThat(stored).isEqualTo("2026-11-16");
    }

    /**
     * The series-key invariant. NULL <em>is</em> "the household's" (adr/0052),
     * so a shared tracker's rows must carry no member - otherwise the fold
     * splits one household series into one line per typist and nothing fails.
     */
    @Test
    void aSharedTrackersReadingsCarryNoMember() {
        Member ana = givenMember("rd-shared");
        Member ben = givenMember("rd-shared2");
        String id = tracker(ana, "Spend " + ana.username(), "MONTH", "SHARED", "SUM",
                ben.id().toString());

        record(ana.session(), id, "2026-11-03", "142");
        record(ben.session(), id, "2026-11-11", "318");

        Integer withMember = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tracker_reading WHERE tracker_id = ?::uuid "
                        + "AND member_id IS NOT NULL", Integer.class, id);
        assertThat(withMember).isZero();

        // Both numbers are in the one series, and the fold adds them up.
        ResponseEntity<Map> series = rest.exchange(
                "/api/trackers/" + id + "/series?from=2026-11-01&to=2026-11-01",
                HttpMethod.GET, new HttpEntity<>(headersFor(ana.session())), Map.class);
        List<Map<String, Object>> lines = (List<Map<String, Object>>) series.getBody().get("series");
        assertThat(lines).hasSize(1);
        // Compared numerically: the column is NUMERIC(12,3) and Jackson hands
        // back a Double, so an isEqualTo(460) against an Integer fails on the
        // type rather than on the arithmetic.
        assertThat(new java.math.BigDecimal(
                String.valueOf(((List<Object>) lines.get(0).get("values")).get(0))))
                .isEqualByComparingTo("460");
    }

    @Test
    void aPerMemberTrackersReadingsCarryTheMemberWhoRecordedThem() {
        Member ana = givenMember("rd-mine");
        Member ben = givenMember("rd-theirs");
        String id = tracker(ana, "Weight " + ana.username(), "DAY", "PER_MEMBER", "LATEST",
                ben.id().toString());

        record(ana.session(), id, "2026-11-19", "72.4");
        record(ben.session(), id, "2026-11-19", "81.0");

        Integer unattributed = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tracker_reading WHERE tracker_id = ?::uuid "
                        + "AND member_id IS NULL", Integer.class, id);
        assertThat(unattributed).isZero();

        ResponseEntity<Map> series = rest.exchange(
                "/api/trackers/" + id + "/series?from=2026-11-19&to=2026-11-19",
                HttpMethod.GET, new HttpEntity<>(headersFor(ana.session())), Map.class);
        // Two lines, overlaid, and each member sees both (R164).
        assertThat((List<Object>) series.getBody().get("series")).hasSize(2);
    }

    /**
     * R163, against the database: there is deliberately no unique index on
     * (tracker, member, period_start), so three shopping trips are three rows
     * that the rollup turns into one November.
     */
    @Test
    void severalReadingsMayShareOneBucket() {
        Member ana = givenMember("rd-many");
        String id = tracker(ana, "Spend " + ana.username(), "MONTH", "SHARED", "SUM");

        assertThat(record(ana.session(), id, "2026-11-03", "142").getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
        assertThat(record(ana.session(), id, "2026-11-11", "318").getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
        assertThat(record(ana.session(), id, "2026-11-24", "520").getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        Integer rows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tracker_reading WHERE tracker_id = ?::uuid",
                Integer.class, id);
        assertThat(rows).isEqualTo(3);
    }

    // --- R165 over HTTP -----------------------------------------------------

    @Test
    void aMemberMayNotCorrectAnotherMembersNumber() {
        Member ana = givenMember("rd-owner");
        Member ben = givenMember("rd-guest");
        String id = tracker(ana, "Weight " + ana.username(), "DAY", "PER_MEMBER", "LATEST",
                ben.id().toString());
        String reading = (String) record(ana.session(), id, "2026-11-19", "72.4").getBody()
                .get("id");

        ResponseEntity<Map> edited = rest.exchange("/api/tracker-readings/" + reading,
                HttpMethod.PATCH, new HttpEntity<>(
                        "{\"version\":0,\"periodStart\":\"2026-11-19\",\"amount\":99}",
                        headersFor(ben.session())), Map.class);

        // Visible on the chart, so 403 rather than 404 - the two refusals are
        // not interchangeable.
        assertThat(edited.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void anyMemberMayCorrectASharedTrackersNumber() {
        Member ana = givenMember("rd-sh-owner");
        Member ben = givenMember("rd-sh-guest");
        String id = tracker(ana, "Spend " + ana.username(), "MONTH", "SHARED", "SUM",
                ben.id().toString());
        String reading = (String) record(ana.session(), id, "2026-11-03", "142").getBody()
                .get("id");

        ResponseEntity<Map> edited = rest.exchange("/api/tracker-readings/" + reading,
                HttpMethod.PATCH, new HttpEntity<>(
                        "{\"version\":0,\"periodStart\":\"2026-11-03\",\"amount\":241}",
                        headersFor(ben.session())), Map.class);

        assertThat(edited.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(edited.getBody().get("editable")).isEqualTo(true);
    }

    @Test
    void aReadingOnATrackerTheCallerCannotSeeIsNotFound() {
        Member ana = givenMember("rd-hidden");
        Member ben = givenMember("rd-stranger");
        String id = tracker(ana, "Private " + ana.username(), "DAY", "PER_MEMBER", "LATEST");
        String reading = (String) record(ana.session(), id, "2026-11-19", "72.4").getBody()
                .get("id");

        ResponseEntity<Map> deleted = rest.exchange("/api/tracker-readings/" + reading,
                HttpMethod.DELETE, new HttpEntity<>(headersFor(ben.session())), Map.class);

        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void tellsAMemberWhenSomebodyElseChangedTheNumberFirst() {
        Member ana = givenMember("rd-stale");
        String id = tracker(ana, "Weight " + ana.username(), "DAY", "PER_MEMBER", "LATEST");
        String reading = (String) record(ana.session(), id, "2026-11-19", "72.4").getBody()
                .get("id");
        rest.exchange("/api/tracker-readings/" + reading, HttpMethod.PATCH, new HttpEntity<>(
                "{\"version\":0,\"periodStart\":\"2026-11-19\",\"amount\":72.5}",
                headersFor(ana.session())), Map.class);

        ResponseEntity<Map> again = rest.exchange("/api/tracker-readings/" + reading,
                HttpMethod.PATCH, new HttpEntity<>(
                        "{\"version\":0,\"periodStart\":\"2026-11-19\",\"amount\":72.6}",
                        headersFor(ana.session())), Map.class);

        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(again.getBody().get("code")).isEqualTo("STALE_TRACKER_READING");
    }

    /** R171, over HTTP: the nudge names what is missing and stops once it is not. */
    @Test
    void theNudgeNamesADailyTrackerWithNothingRecordedTodayAndThenStops() {
        Member ana = givenMember("rd-pending");
        String daily = tracker(ana, "Weight " + ana.username(), "DAY", "PER_MEMBER", "LATEST");
        String monthly = tracker(ana, "Spend " + ana.username(), "MONTH", "SHARED", "SUM");

        ResponseEntity<List> before = rest.exchange("/api/trackers/pending", HttpMethod.GET,
                new HttpEntity<>(headersFor(ana.session())), List.class);
        List<Map<String, Object>> pending = (List<Map<String, Object>>) before.getBody();
        assertThat(pending).anyMatch(row -> daily.equals(row.get("id")));
        // A monthly tracker never nudges: a month-long reproach is not a
        // reminder.
        assertThat(pending).noneMatch(row -> monthly.equals(row.get("id")));

        String today = (String) pending.stream()
                .filter(row -> daily.equals(row.get("id")))
                .findFirst().orElseThrow().get("periodStart");
        record(ana.session(), daily, today, "72.4");

        ResponseEntity<List> after = rest.exchange("/api/trackers/pending", HttpMethod.GET,
                new HttpEntity<>(headersFor(ana.session())), List.class);
        assertThat((List<Map<String, Object>>) after.getBody())
                .noneMatch(row -> daily.equals(row.get("id")));
    }
}
