package com.hatodo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Lists and their access grants, against real Postgres.
 *
 * <p>This is where the authorization rules have to be proved. A readable()
 * predicate applied in Java after fetching every row passes every unit test and
 * leaks every list, so the negative cases below are the ones that matter.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
class ListApiIntegrationTest extends ApiIntegrationTest {

    /** For backdating interaction buckets: see hitsOnDifferentDays. */
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

    private ResponseEntity<Map> createList(Session session, String name, String... memberIds) {
        String members = memberIds.length == 0
                ? "[]"
                : "[\"" + String.join("\",\"", memberIds) + "\"]";
        return rest.exchange("/api/lists", HttpMethod.POST, new HttpEntity<>(
                "{\"name\":\"%s\",\"icon\":\"GROCERIES\",\"memberIds\":%s}".formatted(name, members),
                headersFor(session)), Map.class);
    }

    @Test
    void createsAListTheOwnerCanSee() {
        Member ana = givenMember("owner");

        ResponseEntity<Map> created = createList(ana.session(), "Groceries " + ana.username());

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().get("owned")).isEqualTo(true);
        assertThat(created.getBody().get("icon")).isEqualTo("GROCERIES");
        assertThat((List<Map<String, Object>>) created.getBody().get("members")).hasSize(1);
    }

    @Test
    void aListGrantedToNobodyElseIsInvisibleAndUndiscoverable() {
        Member ana = givenMember("private-owner");
        Member ben = givenMember("stranger");
        String id = (String) createList(ana.session(), "Private " + ana.username()).getBody().get("id");

        // Not in their list...
        ResponseEntity<List> theirs = rest.exchange("/api/lists", HttpMethod.GET,
                new HttpEntity<>(headersFor(ben.session())), List.class);
        assertThat((List<Map<String, Object>>) theirs.getBody())
                .extracting(list -> list.get("id")).doesNotContain(id);

        // ...and not reachable by its id either. 404, not 403: R19 makes the
        // existence of the list the thing being hidden.
        ResponseEntity<Map> direct = rest.exchange("/api/lists/" + id, HttpMethod.GET,
                new HttpEntity<>(headersFor(ben.session())), Map.class);
        assertThat(direct.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void theAdminGetsNothingOnAListTheyWereNotGranted() {
        // The test that stops a future `|| isAdmin()`: administration is
        // accounts, not list contents (R20).
        Member ana = givenMember("not-admins");
        String id = (String) createList(ana.session(), "Private " + ana.username()).getBody().get("id");

        Session admin = signIn(ADMIN_USERNAME, ADMIN_PASSWORD);
        ResponseEntity<Map> response = rest.exchange("/api/lists/" + id, HttpMethod.GET,
                new HttpEntity<>(headersFor(admin)), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void aGranteeReadsTheListButCannotChangeIt() {
        Member ana = givenMember("sharer");
        Member ben = givenMember("grantee");
        Map body = createList(ana.session(), "Shared " + ana.username(), ben.id().toString()).getBody();
        String id = (String) body.get("id");

        ResponseEntity<Map> read = rest.exchange("/api/lists/" + id, HttpMethod.GET,
                new HttpEntity<>(headersFor(ben.session())), Map.class);
        assertThat(read.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(read.getBody().get("owned")).isEqualTo(false);

        ResponseEntity<Map> write = rest.exchange("/api/lists/" + id, HttpMethod.PATCH,
                new HttpEntity<>("{\"version\":0,\"name\":\"Hijacked\",\"icon\":\"PETS\"}",
                        headersFor(ben.session())), Map.class);
        assertThat(write.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void revokingAccessTurnsAReadableListIntoAMissingOne() {
        Member ana = givenMember("revoker");
        Member ben = givenMember("revoked");
        Map body = createList(ana.session(), "Shared " + ana.username(), ben.id().toString()).getBody();
        String id = (String) body.get("id");

        assertThat(rest.exchange("/api/lists/" + id, HttpMethod.GET,
                new HttpEntity<>(headersFor(ben.session())), Map.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        long version = ((Number) rest.exchange("/api/lists/" + id, HttpMethod.GET,
                new HttpEntity<>(headersFor(ana.session())), Map.class).getBody().get("version")).longValue();
        ResponseEntity<List> revoked = rest.exchange("/api/lists/" + id + "/access", HttpMethod.PUT,
                new HttpEntity<>("{\"version\":%d,\"memberIds\":[]}".formatted(version),
                        headersFor(ana.session())), List.class);
        assertThat(revoked.getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(rest.exchange("/api/lists/" + id, HttpMethod.GET,
                new HttpEntity<>(headersFor(ben.session())), Map.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void theOwnerCannotBeRemovedFromTheirOwnList() {
        Member ana = givenMember("undroppable");
        Map body = createList(ana.session(), "Mine " + ana.username()).getBody();
        String id = (String) body.get("id");
        long version = ((Number) body.get("version")).longValue();

        ResponseEntity<List> after = rest.exchange("/api/lists/" + id + "/access", HttpMethod.PUT,
                new HttpEntity<>("{\"version\":%d,\"memberIds\":[]}".formatted(version),
                        headersFor(ana.session())), List.class);

        assertThat((List<Map<String, Object>>) after.getBody())
                .extracting(member -> member.get("id")).containsExactly(ana.id().toString());
    }

    @Test
    void twoOwnersMayEachKeepAListOfTheSameNameButOneOwnerMayNot() {
        Member ana = givenMember("namer-one");
        Member ben = givenMember("namer-two");

        assertThat(createList(ana.session(), "Groceries").getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(createList(ben.session(), "Groceries").getStatusCode())
                .as("a different owner is a different audience")
                .isEqualTo(HttpStatus.CREATED);

        ResponseEntity<Map> clash = createList(ana.session(), "groceries");
        assertThat(clash.getStatusCode()).as("the unique index must surface as 409, not 500")
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void aStaleVersionIsRefusedRatherThanOverwriting() {
        Member ana = givenMember("editor");
        Map body = createList(ana.session(), "Editable " + ana.username()).getBody();
        String id = (String) body.get("id");
        long version = ((Number) body.get("version")).longValue();

        assertThat(rest.exchange("/api/lists/" + id, HttpMethod.PATCH,
                new HttpEntity<>("{\"version\":%d,\"name\":\"First\",\"icon\":\"PETS\"}".formatted(version),
                        headersFor(ana.session())), Map.class).getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> second = rest.exchange("/api/lists/" + id, HttpMethod.PATCH,
                new HttpEntity<>("{\"version\":%d,\"name\":\"Second\",\"icon\":\"PETS\"}".formatted(version),
                        headersFor(ana.session())), Map.class);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(second.getBody().get("code")).isEqualTo("STALE_LIST");
        assertThat(rest.exchange("/api/lists/" + id, HttpMethod.GET,
                new HttpEntity<>(headersFor(ana.session())), Map.class).getBody().get("name"))
                .isEqualTo("First");
    }

    @Test
    void changingTheAudienceMovesTheVersionSoAConcurrentEditorIsTold() {
        Member ana = givenMember("audience-editor");
        Member ben = givenMember("audience-member");
        Map body = createList(ana.session(), "Shifting " + ana.username()).getBody();
        String id = (String) body.get("id");
        long version = ((Number) body.get("version")).longValue();

        rest.exchange("/api/lists/" + id + "/access", HttpMethod.PUT,
                new HttpEntity<>("{\"version\":%d,\"memberIds\":[\"%s\"]}".formatted(version, ben.id()),
                        headersFor(ana.session())), List.class);

        // The other tab still holds the old version.
        ResponseEntity<Map> stale = rest.exchange("/api/lists/" + id, HttpMethod.PATCH,
                new HttpEntity<>("{\"version\":%d,\"name\":\"Renamed\",\"icon\":\"PETS\"}".formatted(version),
                        headersFor(ana.session())), Map.class);
        assertThat(stale.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void pinningIsPersonalAndShowsUpInTheOwnSidebarOnly() {
        Member ana = givenMember("pinner");
        Member ben = givenMember("pin-witness");
        String id = (String) createList(ana.session(), "Pinnable " + ana.username(),
                ben.id().toString()).getBody().get("id");

        assertThat(rest.exchange("/api/lists/" + id + "/pin", HttpMethod.PUT,
                new HttpEntity<>(headersFor(ana.session())), Void.class).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(pinnedIds(ana.session())).contains(id);
        // R105: the two sections partition the member's lists, so a pinned list
        // leaves the group rather than appearing in both.
        assertThat(sidebarIds(ana.session())).doesNotContain(id);
        assertThat(pinnedIds(ben.session())).as("one member's pins are their own").doesNotContain(id);
        assertThat(sidebarIds(ben.session())).as("and it is still in their group").contains(id);

        rest.exchange("/api/lists/" + id + "/pin", HttpMethod.DELETE,
                new HttpEntity<>(headersFor(ana.session())), Void.class);
        assertThat(pinnedIds(ana.session())).doesNotContain(id);
        assertThat(sidebarIds(ana.session())).as("and comes back to the group").contains(id);
    }

    @Test
    void touchingTwiceInsideTheWindowCountsOnce() {
        // Pure SQL - both conditional upserts are what keep a visit off the hot
        // path, and nothing but a real database can show that they hold. Read
        // through the ordering rather than the row, because the ordering is the
        // only thing that ever reads it: two touches of A and one of B must
        // still leave A ahead, which is only true if the second touch of A was
        // *not* counted.
        Member ana = givenMember("toucher");
        String a = (String) createList(ana.session(), "Aaa " + ana.username()).getBody().get("id");
        String b = (String) createList(ana.session(), "Bbb " + ana.username()).getBody().get("id");

        touch(ana.session(), a);
        touch(ana.session(), a);
        touch(ana.session(), b);

        assertThat(sidebarIds(ana.session())).as("one hit each, so the tie-break decides")
                .startsWith(b);
    }

    @Test
    void theSidebarReachesAListNobodyHasOpened() {
        // R98. The old "recent" query required a list_user_state row, so a list
        // just created - and every list somebody else shared - was absent from
        // the sidebar until it had been opened once.
        Member ana = givenMember("unopened");
        Member ben = givenMember("unopened-sharer");
        String mine = (String) createList(ana.session(), "Mine " + ana.username()).getBody().get("id");
        String theirs = (String) createList(ben.session(), "Theirs " + ben.username(),
                ana.id().toString()).getBody().get("id");

        assertThat(sidebarIds(ana.session())).contains(mine, theirs);
    }

    @Test
    void theSidebarPutsTheListTheMemberUsesMostFirst() {
        // R100. Frequency, not recency: the list opened three times outranks
        // the one opened once afterwards.
        Member ana = givenMember("habitual");
        String often = (String) createList(ana.session(), "Zzz " + ana.username()).getBody().get("id");
        String once = (String) createList(ana.session(), "Aaa " + ana.username()).getBody().get("id");

        // Three separate days, because the dedup window collapses three touches
        // in one sitting into one hit - which is the point of it.
        hitsOnDifferentDays(ana, often, 3);
        touch(ana.session(), once);

        assertThat(sidebarIds(ana.session())).startsWith(often);
    }

    @Test
    void aPinLiftsAListOutOfTheGroupHoweverLittleItIsUsed() {
        // R105. A pin is an explicit instruction, so it is not competing with
        // measured habit at all - it moves the list to a section of its own.
        Member ana = givenMember("pin-over-habit");
        String often = (String) createList(ana.session(), "Often " + ana.username()).getBody().get("id");
        String pinned = (String) createList(ana.session(), "Pinned " + ana.username()).getBody().get("id");

        hitsOnDifferentDays(ana, often, 5);
        rest.exchange("/api/lists/" + pinned + "/pin", HttpMethod.PUT,
                new HttpEntity<>(headersFor(ana.session())), Void.class);

        assertThat(pinnedIds(ana.session())).contains(pinned).doesNotContain(often);
        assertThat(sidebarIds(ana.session())).startsWith(often).doesNotContain(pinned);
    }

    @Test
    void thePinnedSectionIsNotCappedByTheSidebarPreference() {
        // R106. The section scrolls rather than truncating: silently dropping a
        // pin is the sidebar overruling an explicit instruction.
        Member ana = givenMember("many-pins");
        rest.exchange("/api/me/preferences", HttpMethod.PATCH,
                new HttpEntity<>("{\"theme\":\"SYSTEM\",\"sidebarListCount\":2}",
                        headersFor(ana.session())), Map.class);

        for (int index = 0; index < 4; index++) {
            String id = (String) createList(ana.session(), "Pinned " + index + " " + ana.username())
                    .getBody().get("id");
            rest.exchange("/api/lists/" + id + "/pin", HttpMethod.PUT,
                    new HttpEntity<>(headersFor(ana.session())), Void.class);
        }

        assertThat(pinnedIds(ana.session())).hasSize(4);
        assertThat(sidebarIds(ana.session())).isEmpty();
    }

    @Test
    void theSidebarShowsAsManyListsAsTheMemberAsksFor() {
        // R102. Four lists, a preference of two.
        Member ana = givenMember("counter");
        for (int index = 0; index < 4; index++) {
            createList(ana.session(), "Counted " + index + " " + ana.username());
        }

        assertThat(sidebarIds(ana.session())).hasSize(4);

        rest.exchange("/api/me/preferences", HttpMethod.PATCH,
                new HttpEntity<>("{\"theme\":\"SYSTEM\",\"sidebarListCount\":2}",
                        headersFor(ana.session())), Map.class);

        assertThat(sidebarIds(ana.session())).hasSize(2);
    }

    @Test
    void aSidebarSizeOutsideTheBoundsIsRefused() {
        Member ana = givenMember("over-counter");

        assertThat(rest.exchange("/api/me/preferences", HttpMethod.PATCH,
                new HttpEntity<>("{\"theme\":\"SYSTEM\",\"sidebarListCount\":0}",
                        headersFor(ana.session())), Map.class).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(rest.exchange("/api/me/preferences", HttpMethod.PATCH,
                new HttpEntity<>("{\"theme\":\"SYSTEM\",\"sidebarListCount\":99}",
                        headersFor(ana.session())), Map.class).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    /**
     * Counts {@code hits} visits by backdating the buckets a touch wrote.
     *
     * <p>The dedup window means a loop of touches writes one hit, so the only
     * honest way to simulate a habit is to put the hits on different days -
     * which is also what the ordering query actually sums.
     */
    private void hitsOnDifferentDays(Member member, String listId, int hits) {
        touch(member.session(), listId);
        for (int day = 1; day < hits; day++) {
            jdbc.update("""
                    INSERT INTO list_interaction_day (list_id, user_id, on_day, hits, last_at)
                    VALUES (?::uuid, ?::uuid, current_date - ?, 1, now() - make_interval(days => ?))
                    """, listId, member.id().toString(), day, day);
        }
    }

    @Test
    void searchFiltersByNameAndByWhoOwnsIt() {
        Member ana = givenMember("searcher");
        Member ben = givenMember("search-sharer");
        String unique = ana.username();
        createList(ana.session(), "Findable " + unique);
        createList(ben.session(), "Shared in " + unique, ana.id().toString());

        assertThat(names(ana.session(), "?q=Findable")).allMatch(name -> name.startsWith("Findable"));
        assertThat(names(ana.session(), "?scope=owned")).contains("Findable " + unique)
                .doesNotContain("Shared in " + unique);
        assertThat(names(ana.session(), "?scope=shared")).contains("Shared in " + unique)
                .doesNotContain("Findable " + unique);
    }

    @Test
    void theDirectoryNeedsASessionAndSaysNothingAboutSigningIn() {
        assertThat(rest.getForEntity("/api/users", List.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        Member ana = givenMember("directory-reader");
        ResponseEntity<List> members = rest.exchange("/api/users", HttpMethod.GET,
                new HttpEntity<>(headersFor(ana.session())), List.class);

        assertThat(members.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<Map<String, Object>>) members.getBody())
                .isNotEmpty()
                .allSatisfy(member -> assertThat(member.keySet()).containsExactlyInAnyOrder(
                        "id", "displayName", "avatarImageId"));
    }

    @Test
    void writingWithoutCsrfIsRefused() {
        Member ana = givenMember("csrf-less");
        org.springframework.http.HttpHeaders noToken = new org.springframework.http.HttpHeaders();
        noToken.add("Cookie", ana.session().cookie());
        noToken.add("Content-Type", "application/json");

        ResponseEntity<Map> response = rest.exchange("/api/lists", HttpMethod.POST,
                new HttpEntity<>("{\"name\":\"No token\",\"icon\":\"CHECKLIST\"}", noToken), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void anUnknownIconIsRefusedBeforeAnythingIsCreated() {
        Member ana = givenMember("icon-inventor");

        ResponseEntity<Map> response = rest.exchange("/api/lists", HttpMethod.POST,
                new HttpEntity<>("{\"name\":\"Odd\",\"icon\":\"SPACESHIP\"}", headersFor(ana.session())),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    /** One of the sidebar's two sections, in the order it is drawn. */
    private List<String> sidebarIds(Session session, String section) {
        ResponseEntity<Map> sidebar = rest.exchange("/api/lists/sidebar", HttpMethod.GET,
                new HttpEntity<>(headersFor(session)), Map.class);
        return ((List<Map<String, Object>>) sidebar.getBody().get(section)).stream()
                .map(list -> (String) list.get("id")).toList();
    }

    /** The capped group below the pinned section (0012 R98-R100). */
    private List<String> sidebarIds(Session session) {
        return sidebarIds(session, "lists");
    }

    private List<String> pinnedIds(Session session) {
        return sidebarIds(session, "pinned");
    }

    private void touch(Session session, String id) {
        rest.exchange("/api/lists/" + id + "/touch", HttpMethod.POST,
                new HttpEntity<>(headersFor(session)), Void.class);
    }

    private List<String> names(Session session, String query) {
        ResponseEntity<List> response = rest.exchange("/api/lists" + query, HttpMethod.GET,
                new HttpEntity<>(headersFor(session)), List.class);
        return ((List<Map<String, Object>>) response.getBody()).stream()
                .map(list -> (String) list.get("name")).toList();
    }
}
