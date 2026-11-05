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
    void anAnonymousCallerGetsNoCollectionsAndA401() {
        assertThat(rest.getForEntity("/api/data-lists", Map.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
