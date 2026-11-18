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
 * Trackers and their grants, against real Postgres (requirements/0019).
 *
 * <p>This is where the authorization has to be proved rather than asserted. A
 * readable() predicate applied in Java after fetching every row passes every
 * unit test and leaks every tracker, so the negative cases below are the ones
 * that matter.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
class TrackerApiIntegrationTest extends ApiIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    private record Member(String username, UUID id, Session session) {}

    private Member givenMember(String label) {
        String username = label + "-" + UUID.randomUUID().toString().substring(0, 8);
        Session admin = signIn(ADMIN_USERNAME, ADMIN_PASSWORD);
        ResponseEntity<Map> created = rest.exchange("/api/admin/users", HttpMethod.POST,
                new HttpEntity<>("{\"username\":\"%s\",\"displayName\":\"%s\",\"password\":\"pw-%s\"}"
                        .formatted(username, username, username), headersFor(admin)), Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID id = UUID.fromString((String) created.getBody().get("id"));
        return new Member(username, id, signIn(username, "pw-" + username));
    }

    private ResponseEntity<Map> createTracker(Session session, String name, String period,
                                              String scope, String rollup, String... memberIds) {
        String members = memberIds.length == 0
                ? "[]"
                : "[\"" + String.join("\",\"", memberIds) + "\"]";
        return rest.exchange("/api/trackers", HttpMethod.POST, new HttpEntity<>(
                ("{\"name\":\"%s\",\"icon\":\"HEALTH\",\"unit\":\"kg\",\"period\":\"%s\","
                        + "\"scope\":\"%s\",\"rollup\":\"%s\",\"memberIds\":%s}")
                        .formatted(name, period, scope, rollup, members),
                headersFor(session)), Map.class);
    }

    private String newTracker(Member member, String name) {
        return (String) createTracker(member.session(), name, "DAY", "PER_MEMBER", "LATEST")
                .getBody().get("id");
    }

    @Test
    void creatingATrackerGrantsTheOwnerAccess() {
        Member ana = givenMember("tr-owner");

        ResponseEntity<Map> created =
                createTracker(ana.session(), "Weight " + ana.username(), "DAY", "PER_MEMBER",
                        "LATEST");

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().get("owned")).isEqualTo(true);
        assertThat((List<Map<String, Object>>) created.getBody().get("members")).hasSize(1);
    }

    /**
     * The regression guard against adr/0051's rejected designs, end to end.
     *
     * <p>If a tracker were ever a kind of {@code todo_list} it would arrive
     * with a DUE_AT field row, and if it were a kind of {@code data_list} it
     * would arrive with a search vector. It is neither, and the schema is what
     * makes that true rather than nine filters that have to agree.
     */
    @Test
    void aTrackerIsNotReachableFromEitherOfTheOtherTwoAggregates() {
        Member ana = givenMember("tr-sibling");
        String id = newTracker(ana, "Weight " + ana.username());

        Integer asList = jdbc.queryForObject(
                "SELECT COUNT(*) FROM todo_list WHERE id = ?::uuid", Integer.class, id);
        Integer asCollection = jdbc.queryForObject(
                "SELECT COUNT(*) FROM data_list WHERE id = ?::uuid", Integer.class, id);
        Integer fields = jdbc.queryForObject(
                "SELECT COUNT(*) FROM list_field WHERE list_id = ?::uuid", Integer.class, id);

        assertThat(asList).isZero();
        assertThat(asCollection).isZero();
        assertThat(fields).isZero();
    }

    @Test
    void aTrackerIsInvisibleToAMemberWithNoGrant() {
        Member ana = givenMember("tr-private");
        Member ben = givenMember("tr-outsider");
        String id = newTracker(ana, "Private " + ana.username());

        ResponseEntity<Map> seen = rest.exchange("/api/trackers/" + id, HttpMethod.GET,
                new HttpEntity<>(headersFor(ben.session())), Map.class);

        // 404, not 403: the existence of the tracker is what is hidden.
        assertThat(seen.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<List> index = rest.exchange("/api/trackers", HttpMethod.GET,
                new HttpEntity<>(headersFor(ben.session())), List.class);
        assertThat((List<Map<String, Object>>) index.getBody())
                .noneMatch(row -> id.equals(row.get("id")));
    }

    @Test
    void aGrantedMemberSeesItButCannotChangeIt() {
        Member ana = givenMember("tr-sharer");
        Member ben = givenMember("tr-guest");
        String id = (String) createTracker(ana.session(), "Shared " + ana.username(), "DAY",
                "PER_MEMBER", "LATEST", ben.id().toString()).getBody().get("id");

        ResponseEntity<Map> seen = rest.exchange("/api/trackers/" + id, HttpMethod.GET,
                new HttpEntity<>(headersFor(ben.session())), Map.class);
        assertThat(seen.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(seen.getBody().get("owned")).isEqualTo(false);

        ResponseEntity<Map> renamed = rest.exchange("/api/trackers/" + id, HttpMethod.PATCH,
                new HttpEntity<>(("{\"version\":0,\"name\":\"Theirs\",\"icon\":\"HEALTH\","
                        + "\"unit\":\"kg\",\"period\":\"DAY\",\"scope\":\"PER_MEMBER\","
                        + "\"rollup\":\"LATEST\"}"), headersFor(ben.session())), Map.class);

        // Visible, so 403 rather than 404 - and managed is ownership alone.
        assertThat(renamed.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void theSidebarPartitionsPinnedTrackersOutOfTheGroup() {
        Member ana = givenMember("tr-pin");
        String id = newTracker(ana, "Pinned " + ana.username());

        ResponseEntity<Map> before = rest.exchange("/api/trackers/sidebar", HttpMethod.GET,
                new HttpEntity<>(headersFor(ana.session())), Map.class);
        assertThat((List<Map<String, Object>>) before.getBody().get("trackers"))
                .anyMatch(row -> id.equals(row.get("id")));

        rest.exchange("/api/trackers/" + id + "/pin", HttpMethod.PUT,
                new HttpEntity<>(headersFor(ana.session())), Void.class);

        ResponseEntity<Map> after = rest.exchange("/api/trackers/sidebar", HttpMethod.GET,
                new HttpEntity<>(headersFor(ana.session())), Map.class);
        // It leaves the group rather than appearing in both halves - which is
        // also what keeps the group's cap honest.
        assertThat((List<Map<String, Object>>) after.getBody().get("pinned"))
                .anyMatch(row -> id.equals(row.get("id")));
        assertThat((List<Map<String, Object>>) after.getBody().get("trackers"))
                .noneMatch(row -> id.equals(row.get("id")));
    }

    /**
     * The group's outer join must stay LEFT: a tracker created a minute ago has
     * no interaction bucket, and an inner join would hide it until it was
     * opened - the only-what-you-opened behaviour the Recent section was
     * dropped for (0012 R98).
     */
    @Test
    void aTrackerNobodyHasOpenedYetIsStillInTheSidebar() {
        Member ana = givenMember("tr-fresh");
        String id = newTracker(ana, "Brand new " + ana.username());

        ResponseEntity<Map> sidebar = rest.exchange("/api/trackers/sidebar", HttpMethod.GET,
                new HttpEntity<>(headersFor(ana.session())), Map.class);

        assertThat((List<Map<String, Object>>) sidebar.getBody().get("trackers"))
                .anyMatch(row -> id.equals(row.get("id")));
    }

    @Test
    void refusesASecondTrackerWithTheSameNameForOneOwner() {
        Member ana = givenMember("tr-dupe");
        String name = "Weight " + ana.username();
        createTracker(ana.session(), name, "DAY", "PER_MEMBER", "LATEST");

        ResponseEntity<Map> again =
                createTracker(ana.session(), name.toUpperCase(), "DAY", "PER_MEMBER", "LATEST");

        // The index is on lower(name), and the service checks with LOWER() for
        // exactly that reason.
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    /** R161, over HTTP: refused once a number exists, with its own code. */
    @Test
    void refusesToReshapeATrackerThatAlreadyHoldsReadings() {
        Member ana = givenMember("tr-inuse");
        String id = newTracker(ana, "Weight " + ana.username());
        rest.exchange("/api/trackers/" + id + "/readings", HttpMethod.POST,
                new HttpEntity<>("{\"periodStart\":\"2026-11-19\",\"amount\":72.4}",
                        headersFor(ana.session())), Map.class);

        ResponseEntity<Map> reshaped = rest.exchange("/api/trackers/" + id, HttpMethod.PATCH,
                new HttpEntity<>(("{\"version\":0,\"name\":\"Weight %s\",\"icon\":\"HEALTH\","
                        + "\"unit\":\"kg\",\"period\":\"MONTH\",\"scope\":\"PER_MEMBER\","
                        + "\"rollup\":\"LATEST\"}").formatted(ana.username()),
                        headersFor(ana.session())), Map.class);

        assertThat(reshaped.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(reshaped.getBody().get("code")).isEqualTo("TRACKER_IN_USE");
    }

    @Test
    void deletingATrackerTakesItsReadingsWithIt() {
        Member ana = givenMember("tr-delete");
        String id = newTracker(ana, "Weight " + ana.username());
        rest.exchange("/api/trackers/" + id + "/readings", HttpMethod.POST,
                new HttpEntity<>("{\"periodStart\":\"2026-11-19\",\"amount\":72.4}",
                        headersFor(ana.session())), Map.class);

        rest.exchange("/api/trackers/" + id, HttpMethod.DELETE,
                new HttpEntity<>(headersFor(ana.session())), Void.class);

        Integer left = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tracker_reading WHERE tracker_id = ?::uuid",
                Integer.class, id);
        assertThat(left).isZero();
    }
}
