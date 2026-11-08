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
 * Collections and their grants, against real Postgres (requirements/0006 R50).
 *
 * <p>This is where the authorization has to be proved rather than asserted. A
 * readable() predicate applied in Java after fetching every row passes every
 * unit test and leaks every collection, so the negative cases below are the
 * ones that matter.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
class DataListApiIntegrationTest extends ApiIntegrationTest {

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

    private ResponseEntity<Map> createCollection(Session session, String name,
                                                 String... memberIds) {
        String members = memberIds.length == 0
                ? "[]"
                : "[\"" + String.join("\",\"", memberIds) + "\"]";
        return rest.exchange("/api/data-lists", HttpMethod.POST, new HttpEntity<>(
                "{\"name\":\"%s\",\"icon\":\"GROCERIES\",\"memberIds\":%s}"
                        .formatted(name, members),
                headersFor(session)), Map.class);
    }

    private String newCollection(Member member, String name) {
        return (String) createCollection(member.session(), name).getBody().get("id");
    }

    @Test
    void creatingACollectionGrantsTheOwnerAccess() {
        Member ana = givenMember("coll-owner");

        ResponseEntity<Map> created = createCollection(ana.session(), "Recipes " + ana.username());

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().get("owned")).isEqualTo(true);
        assertThat((List<Map<String, Object>>) created.getBody().get("members")).hasSize(1);
    }

    /**
     * The regression guard against adr/0029's rejected design, end to end.
     *
     * <p>If a collection were ever a kind of {@code todo_list}, it would be
     * created through {@code TodoListService.create} and arrive with a DUE_AT
     * field row - a due date on a recipe, which R50 exists to rule out.
     */
    @Test
    void aCollectionDeclaresNoFieldsAndAboveAllNoDueDate() {
        Member ana = givenMember("coll-nofields");
        String id = newCollection(ana, "Recipes " + ana.username());

        Long fieldRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM list_field WHERE list_id = ?::uuid", Long.class, id);
        assertThat(fieldRows).isZero();
        // And it is not a todo_list at all.
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM todo_list WHERE id = ?::uuid",
                Long.class, id)).isZero();
    }

    @Test
    void aCollectionGrantedToNobodyElseIsInvisibleAndUndiscoverable() {
        Member ana = givenMember("coll-private");
        Member ben = givenMember("coll-outsider");
        String id = newCollection(ana, "Private " + ana.username());

        assertThat(rest.exchange("/api/data-lists/" + id, HttpMethod.GET,
                new HttpEntity<>(headersFor(ben.session())), Map.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<List> theirs = rest.exchange("/api/data-lists", HttpMethod.GET,
                new HttpEntity<>(headersFor(ben.session())), List.class);
        assertThat((List<Map<String, Object>>) theirs.getBody())
                .noneMatch(row -> id.equals(row.get("id")));
    }

    @Test
    void aGrantHolderCanSeeItButNotChangeIt() {
        Member ana = givenMember("coll-sharer");
        Member ben = givenMember("coll-guest");
        String id = newCollection(ana, "Shared " + ana.username());

        rest.exchange("/api/data-lists/" + id + "/access", HttpMethod.PUT, new HttpEntity<>(
                "{\"version\":0,\"memberIds\":[\"%s\"]}".formatted(ben.id()),
                headersFor(ana.session())), List.class);

        assertThat(rest.exchange("/api/data-lists/" + id, HttpMethod.GET,
                new HttpEntity<>(headersFor(ben.session())), Map.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        // Access without ownership is 403, not 404: they can see it, so
        // hiding its existence would be absurd.
        assertThat(rest.exchange("/api/data-lists/" + id, HttpMethod.PATCH, new HttpEntity<>(
                "{\"version\":1,\"name\":\"Renamed\",\"icon\":\"HOME\"}",
                headersFor(ben.session())), Map.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void aDuplicateNamePerOwnerAnswers409() {
        Member ana = givenMember("coll-dupe");
        String name = "Recipes " + ana.username();
        createCollection(ana.session(), name);

        assertThat(createCollection(ana.session(), name).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void twoOwnersMayEachKeepARecipesCollection() {
        Member ana = givenMember("coll-a");
        Member ben = givenMember("coll-b");

        assertThat(createCollection(ana.session(), "Recipes").getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
        assertThat(createCollection(ben.session(), "Recipes").getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
    }

    /**
     * The namespaces are separate on purpose: "Restaurants" the collection and
     * "Restaurants" the todo list are different things and one member may want
     * both.
     */
    @Test
    void aCollectionAndATodoListMayShareAName() {
        Member ana = givenMember("coll-namespace");
        String name = "Restaurants " + ana.username();

        assertThat(rest.exchange("/api/lists", HttpMethod.POST, new HttpEntity<>(
                "{\"name\":\"%s\",\"icon\":\"RESTAURANT\",\"memberIds\":[]}".formatted(name),
                headersFor(ana.session())), Map.class).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
        assertThat(createCollection(ana.session(), name).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void aStaleUpdateAnswers409() {
        Member ana = givenMember("coll-stale");
        String id = newCollection(ana, "Recipes " + ana.username());

        ResponseEntity<Map> stale = rest.exchange("/api/data-lists/" + id, HttpMethod.PATCH,
                new HttpEntity<>("{\"version\":99,\"name\":\"Renamed\",\"icon\":\"HOME\"}",
                        headersFor(ana.session())), Map.class);

        assertThat(stale.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(stale.getBody().get("code")).isEqualTo("STALE_DATA_LIST");
    }

    @Test
    void editingTheAccessListMovesTheVersionSoASecondEditorIsTold() {
        Member ana = givenMember("coll-version");
        Member ben = givenMember("coll-version-guest");
        String id = newCollection(ana, "Shared " + ana.username());

        rest.exchange("/api/data-lists/" + id + "/access", HttpMethod.PUT, new HttpEntity<>(
                "{\"version\":0,\"memberIds\":[\"%s\"]}".formatted(ben.id()),
                headersFor(ana.session())), List.class);

        // Hibernate will not dirty-check a row whose own columns are
        // untouched, so this is what markChanged is for.
        ResponseEntity<Map> reread = rest.exchange("/api/data-lists/" + id, HttpMethod.GET,
                new HttpEntity<>(headersFor(ana.session())), Map.class);
        assertThat(((Number) reread.getBody().get("version")).longValue()).isEqualTo(1L);
    }

    @Test
    void revokingAGrantHidesTheEntriesToo() {
        Member ana = givenMember("coll-revoke");
        Member ben = givenMember("coll-revoked");
        String id = newCollection(ana, "Shared " + ana.username());
        rest.exchange("/api/data-lists/" + id + "/access", HttpMethod.PUT, new HttpEntity<>(
                "{\"version\":0,\"memberIds\":[\"%s\"]}".formatted(ben.id()),
                headersFor(ana.session())), List.class);
        rest.exchange("/api/data-lists/" + id + "/entries", HttpMethod.POST, new HttpEntity<>(
                "{\"name\":\"Tiramisu\"}", headersFor(ana.session())), Map.class);

        assertThat(rest.exchange("/api/data-lists/" + id + "/entries", HttpMethod.GET,
                new HttpEntity<>(headersFor(ben.session())), List.class).getBody()).hasSize(1);

        rest.exchange("/api/data-lists/" + id + "/access", HttpMethod.PUT, new HttpEntity<>(
                "{\"version\":1,\"memberIds\":[]}", headersFor(ana.session())), List.class);

        // Map, not List: a 404 carries the error object, and asking for a
        // List here fails to deserialise rather than asserting the status.
        assertThat(rest.exchange("/api/data-lists/" + id + "/entries", HttpMethod.GET,
                new HttpEntity<>(headersFor(ben.session())), Map.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void theOwnerCannotBeRemovedFromTheirOwnCollection() {
        Member ana = givenMember("coll-selfremove");
        String id = newCollection(ana, "Recipes " + ana.username());

        rest.exchange("/api/data-lists/" + id + "/access", HttpMethod.PUT, new HttpEntity<>(
                "{\"version\":0,\"memberIds\":[]}", headersFor(ana.session())), List.class);

        assertThat(rest.exchange("/api/data-lists/" + id, HttpMethod.GET,
                new HttpEntity<>(headersFor(ana.session())), Map.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void deletingACollectionTakesItsEntriesWithIt() {
        Member ana = givenMember("coll-delete");
        String id = newCollection(ana, "Recipes " + ana.username());
        rest.exchange("/api/data-lists/" + id + "/entries", HttpMethod.POST, new HttpEntity<>(
                "{\"name\":\"Tiramisu\"}", headersFor(ana.session())), Map.class);

        rest.exchange("/api/data-lists/" + id, HttpMethod.DELETE,
                new HttpEntity<>(headersFor(ana.session())), Void.class);

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM data_entry WHERE data_list_id = ?::uuid", Long.class, id))
                .isZero();
    }

    @Test
    void scopeSeparatesWhatIOwnFromWhatIWasGiven() {
        Member ana = givenMember("coll-scope");
        Member ben = givenMember("coll-scope-guest");
        String mine = newCollection(ana, "Mine " + ana.username());
        String theirs = newCollection(ben, "Theirs " + ben.username());
        rest.exchange("/api/data-lists/" + theirs + "/access", HttpMethod.PUT, new HttpEntity<>(
                "{\"version\":0,\"memberIds\":[\"%s\"]}".formatted(ana.id()),
                headersFor(ben.session())), List.class);

        ResponseEntity<List> owned = rest.exchange("/api/data-lists?scope=owned", HttpMethod.GET,
                new HttpEntity<>(headersFor(ana.session())), List.class);
        assertThat((List<Map<String, Object>>) owned.getBody())
                .anyMatch(row -> mine.equals(row.get("id")))
                .noneMatch(row -> theirs.equals(row.get("id")));

        ResponseEntity<List> shared = rest.exchange("/api/data-lists?scope=shared", HttpMethod.GET,
                new HttpEntity<>(headersFor(ana.session())), List.class);
        assertThat((List<Map<String, Object>>) shared.getBody())
                .anyMatch(row -> theirs.equals(row.get("id")));
    }

    @Test
    void theSidebarReachesEveryCollectionAndOrdersItByUse() {
        // 0012 R98, R100. A collection has no pin and no recency row
        // (adr/0029), so its buckets are the only thing the order can read.
        Member ana = givenMember("coll-sidebar");
        Member ben = givenMember("coll-sidebar-sharer");
        String often = newCollection(ana, "Zzz " + ana.username());
        String once = newCollection(ana, "Aaa " + ana.username());
        String theirs = newCollection(ben, "Theirs " + ben.username());
        rest.exchange("/api/data-lists/" + theirs + "/access", HttpMethod.PUT, new HttpEntity<>(
                "{\"version\":0,\"memberIds\":[\"%s\"]}".formatted(ana.id()),
                headersFor(ben.session())), List.class);

        hitsOnDifferentDays(ana, often, 3);
        touch(ana.session(), once);

        assertThat(sidebarIds(ana.session()))
                .as("shared as well as owned, opened or not (R98)")
                .contains(often, once, theirs);
        assertThat(sidebarIds(ana.session())).as("most used first (R100)").startsWith(often);
    }

    @Test
    void touchingACollectionTwiceInsideTheWindowCountsOnce() {
        // The dedup window lives inside the bucket upsert, because a collection
        // has no recency row to read it from (adr/0041). Only a real database
        // can show that the ON CONFLICT ... WHERE holds.
        Member ana = givenMember("coll-toucher");
        String id = newCollection(ana, "Touched " + ana.username());

        touch(ana.session(), id);
        touch(ana.session(), id);

        assertThat(jdbc.queryForObject("""
                SELECT hits FROM data_list_interaction_day
                WHERE data_list_id = ?::uuid AND user_id = ?::uuid
                """, Integer.class, id, ana.id().toString())).isEqualTo(1);
    }

    @Test
    void touchingACollectionNobodyGrantedIsA404() {
        Member ana = givenMember("coll-touch-owner");
        Member ben = givenMember("coll-touch-stranger");
        String id = newCollection(ana, "Private " + ana.username());

        assertThat(rest.exchange("/api/data-lists/" + id + "/touch", HttpMethod.POST,
                new HttpEntity<>(headersFor(ben.session())), Map.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    private List<String> sidebarIds(Session session, String section) {
        ResponseEntity<Map> sidebar = rest.exchange("/api/data-lists/sidebar", HttpMethod.GET,
                new HttpEntity<>(headersFor(session)), Map.class);
        return ((List<Map<String, Object>>) sidebar.getBody().get(section)).stream()
                .map(row -> (String) row.get("id")).toList();
    }

    private List<String> sidebarIds(Session session) {
        return sidebarIds(session, "collections");
    }

    private List<String> pinnedIds(Session session) {
        return sidebarIds(session, "pinned");
    }

    private void touch(Session session, String id) {
        rest.exchange("/api/data-lists/" + id + "/touch", HttpMethod.POST,
                new HttpEntity<>(headersFor(session)), Void.class);
    }

    /** As in the list suite: a habit can only be simulated across days. */
    private void hitsOnDifferentDays(Member member, String collectionId, int hits) {
        touch(member.session(), collectionId);
        for (int day = 1; day < hits; day++) {
            jdbc.update("""
                    INSERT INTO data_list_interaction_day
                        (data_list_id, user_id, on_day, hits, last_at)
                    VALUES (?::uuid, ?::uuid, current_date - ?, 1, now() - make_interval(days => ?))
                    """, collectionId, member.id().toString(), day, day);
        }
    }

    @Test
    void pinningACollectionIsPersonalAndMovesItToItsOwnSection() {
        // 0012 R104, R105 and adr/0043: a collection had no per-member state at
        // all before this - list_user_state.list_id is an FK to todo_list and
        // stays that way, which is why this is a table of its own.
        Member ana = givenMember("coll-pinner");
        Member ben = givenMember("coll-pin-witness");
        String id = newCollection(ana, "Pinnable " + ana.username());
        rest.exchange("/api/data-lists/" + id + "/access", HttpMethod.PUT, new HttpEntity<>(
                "{\"version\":0,\"memberIds\":[\"%s\"]}".formatted(ben.id()),
                headersFor(ana.session())), List.class);

        assertThat(rest.exchange("/api/data-lists/" + id + "/pin", HttpMethod.PUT,
                new HttpEntity<>(headersFor(ana.session())), Void.class).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(pinnedIds(ana.session())).contains(id);
        assertThat(sidebarIds(ana.session())).doesNotContain(id);
        assertThat(pinnedIds(ben.session())).as("one member's pins are their own")
                .doesNotContain(id);
        assertThat(sidebarIds(ben.session())).contains(id);

        rest.exchange("/api/data-lists/" + id + "/pin", HttpMethod.DELETE,
                new HttpEntity<>(headersFor(ana.session())), Void.class);
        assertThat(pinnedIds(ana.session())).doesNotContain(id);
        assertThat(sidebarIds(ana.session())).contains(id);
    }

    @Test
    void pinningACollectionNobodyGrantedIsA404() {
        Member ana = givenMember("coll-pin-owner");
        Member ben = givenMember("coll-pin-stranger");
        String id = newCollection(ana, "Private " + ana.username());

        assertThat(rest.exchange("/api/data-lists/" + id + "/pin", HttpMethod.PUT,
                new HttpEntity<>(headersFor(ben.session())), Map.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void anAnonymousCallerGetsNoCollectionsAndA401() {
        assertThat(rest.getForEntity("/api/data-lists", Map.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
