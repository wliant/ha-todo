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

/** Entries: who may write them, and what is stored when they do. */
@SuppressWarnings({"unchecked", "rawtypes"})
class DataEntryApiIntegrationTest extends ApiIntegrationTest {

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

    private String newCollection(Member member, String name) {
        return (String) rest.exchange("/api/data-lists", HttpMethod.POST, new HttpEntity<>(
                "{\"name\":\"%s\",\"icon\":\"GROCERIES\",\"memberIds\":[]}".formatted(name),
                headersFor(member.session())), Map.class).getBody().get("id");
    }

    private void share(Member owner, String collectionId, long version, Member with) {
        rest.exchange("/api/data-lists/" + collectionId + "/access", HttpMethod.PUT,
                new HttpEntity<>("{\"version\":%d,\"memberIds\":[\"%s\"]}"
                        .formatted(version, with.id()), headersFor(owner.session())), List.class);
    }

    private ResponseEntity<Map> addEntry(Session session, String collectionId, String json) {
        return rest.exchange("/api/data-lists/" + collectionId + "/entries", HttpMethod.POST,
                new HttpEntity<>(json, headersFor(session)), Map.class);
    }

    @Test
    void anyGrantHolderMayAddEditAndDeleteAnEntry() {
        // R60: everyone who can see a collection is a peer on its entries,
        // which is the rule items already follow.
        Member ana = givenMember("entry-owner");
        Member ben = givenMember("entry-peer");
        String id = newCollection(ana, "Shared " + ana.username());
        share(ana, id, 0, ben);

        ResponseEntity<Map> created = addEntry(ben.session(), id, "{\"name\":\"Tiramisu\"}");
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String entryId = (String) created.getBody().get("id");

        assertThat(rest.exchange("/api/data-entries/" + entryId, HttpMethod.PATCH,
                new HttpEntity<>("{\"version\":0,\"name\":\"Tiramisu (better)\"}",
                        headersFor(ana.session())), Map.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(rest.exchange("/api/data-entries/" + entryId + "?version=1",
                HttpMethod.DELETE, new HttpEntity<>(headersFor(ben.session())), Void.class)
                .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void anEntryOnAnInvisibleCollectionAnswers404() {
        Member ana = givenMember("entry-private");
        Member ben = givenMember("entry-outsider");
        String id = newCollection(ana, "Private " + ana.username());
        String entryId = (String) addEntry(ana.session(), id, "{\"name\":\"Secret\"}")
                .getBody().get("id");

        assertThat(rest.exchange("/api/data-entries/" + entryId, HttpMethod.GET,
                new HttpEntity<>(headersFor(ben.session())), Map.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        // And writing to it is equally invisible, not forbidden.
        assertThat(addEntry(ben.session(), id, "{\"name\":\"Sneaky\"}").getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void detailHtmlIsSanitizedOnWrite() {
        Member ana = givenMember("entry-xss");
        String id = newCollection(ana, "Recipes " + ana.username());

        ResponseEntity<Map> created = addEntry(ana.session(), id,
                "{\"name\":\"Nasty\",\"detailHtml\":"
                + "\"<p>ok</p><script>alert(1)</script><img src=x onerror=alert(1)>\"}");

        String stored = (String) created.getBody().get("detailHtml");
        assertThat(stored).contains("ok").doesNotContain("script").doesNotContain("onerror");
    }

    @Test
    void detailTextTracksDetailHtmlWithWordBoundariesIntact() {
        Member ana = givenMember("entry-detailtext");
        String id = newCollection(ana, "Recipes " + ana.username());
        String entryId = (String) addEntry(ana.session(), id,
                "{\"name\":\"Tiramisu\",\"detailHtml\":"
                + "\"<ul><li>milk</li><li>sugar</li></ul>\"}").getBody().get("id");

        String detailText = jdbc.queryForObject(
                "SELECT detail_text FROM data_entry WHERE id = ?::uuid", String.class, entryId);

        // "milksugar" would be one lexeme matching nothing a member types.
        assertThat(detailText).isEqualTo("milk sugar");
    }

    @Test
    void editingTheDetailRewritesTheIndexedTextToo() {
        Member ana = givenMember("entry-reindex");
        String id = newCollection(ana, "Recipes " + ana.username());
        String entryId = (String) addEntry(ana.session(), id,
                "{\"name\":\"Tiramisu\",\"detailHtml\":\"<p>espresso</p>\"}")
                .getBody().get("id");

        rest.exchange("/api/data-entries/" + entryId, HttpMethod.PATCH, new HttpEntity<>(
                "{\"version\":0,\"name\":\"Tiramisu\",\"detailHtml\":\"<p>decaf</p>\"}",
                headersFor(ana.session())), Map.class);

        assertThat(jdbc.queryForObject("SELECT detail_text FROM data_entry WHERE id = ?::uuid",
                String.class, entryId)).isEqualTo("decaf");
    }

    @Test
    void tagsAreStoredLowercasedDedupedAndSorted() {
        Member ana = givenMember("entry-tags");
        String id = newCollection(ana, "Recipes " + ana.username());

        ResponseEntity<Map> created = addEntry(ana.session(), id,
                "{\"name\":\"Tiramisu\",\"tags\":[\"ITALIAN\",\" desserts \",\"italian\"]}");

        assertThat((List<String>) created.getBody().get("tags"))
                .containsExactly("desserts", "italian");
    }

    @Test
    void tagsTextTracksTheTagsArray() {
        Member ana = givenMember("entry-tagstext");
        String id = newCollection(ana, "Recipes " + ana.username());
        String entryId = (String) addEntry(ana.session(), id,
                "{\"name\":\"Tiramisu\",\"tags\":[\"italian\",\"desserts\"]}")
                .getBody().get("id");

        // The column the generated search_vector actually reads (adr/0033).
        assertThat(jdbc.queryForObject("SELECT tags_text FROM data_entry WHERE id = ?::uuid",
                String.class, entryId)).isEqualTo("desserts italian");
    }

    @Test
    void anOverLongTagAnswers400WithASentence() {
        Member ana = givenMember("entry-badtag");
        String id = newCollection(ana, "Recipes " + ana.username());

        ResponseEntity<Map> refused = addEntry(ana.session(), id,
                "{\"name\":\"Tiramisu\",\"tags\":[\"%s\"]}".formatted("x".repeat(41)));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat((String) refused.getBody().get("error")).contains("too long");
    }

    @Test
    void anEntryWithNoNameAnswers400() {
        Member ana = givenMember("entry-noname");
        String id = newCollection(ana, "Recipes " + ana.username());

        assertThat(addEntry(ana.session(), id, "{\"name\":\"  \"}").getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void aStaleEditAnswers409AndCarriesTheEntryAsItActuallyIs() {
        Member ana = givenMember("entry-stale");
        Member ben = givenMember("entry-stale-peer");
        String id = newCollection(ana, "Shared " + ana.username());
        share(ana, id, 0, ben);
        String entryId = (String) addEntry(ana.session(), id, "{\"name\":\"Tiramisu\"}")
                .getBody().get("id");

        // Ben writes first, from version 0.
        rest.exchange("/api/data-entries/" + entryId, HttpMethod.PATCH, new HttpEntity<>(
                "{\"version\":0,\"name\":\"Ben's name\"}", headersFor(ben.session())), Map.class);

        // Ana writes from the same stale view.
        ResponseEntity<Map> conflict = rest.exchange("/api/data-entries/" + entryId,
                HttpMethod.PATCH, new HttpEntity<>("{\"version\":0,\"name\":\"Ana's name\"}",
                        headersFor(ana.session())), Map.class);

        assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(conflict.getBody().get("code")).isEqualTo("STALE_DATA_ENTRY");
        // The entry as it is, read in a fresh transaction - which is what lets
        // the client take the server's copy outright instead of guessing.
        Map<String, Object> current = (Map<String, Object>) conflict.getBody().get("entry");
        assertThat(current).isNotNull();
        assertThat(current.get("name")).isEqualTo("Ben's name");
    }

    @Test
    void aStaleDeleteAnswers409RatherThanRemovingSomethingElsesEdit() {
        Member ana = givenMember("entry-staledel");
        String id = newCollection(ana, "Recipes " + ana.username());
        String entryId = (String) addEntry(ana.session(), id, "{\"name\":\"Tiramisu\"}")
                .getBody().get("id");
        rest.exchange("/api/data-entries/" + entryId, HttpMethod.PATCH, new HttpEntity<>(
                "{\"version\":0,\"name\":\"Edited\"}", headersFor(ana.session())), Map.class);

        assertThat(rest.exchange("/api/data-entries/" + entryId + "?version=0",
                HttpMethod.DELETE, new HttpEntity<>(headersFor(ana.session())), Map.class)
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void tagSuggestionsComeFromTheCollectionAndCarryCounts() {
        Member ana = givenMember("entry-suggest");
        String id = newCollection(ana, "Recipes " + ana.username());
        addEntry(ana.session(), id, "{\"name\":\"A\",\"tags\":[\"italian\",\"quick\"]}");
        addEntry(ana.session(), id, "{\"name\":\"B\",\"tags\":[\"italian\"]}");

        ResponseEntity<List> suggestions = rest.exchange(
                "/api/data-lists/" + id + "/tags", HttpMethod.GET,
                new HttpEntity<>(headersFor(ana.session())), List.class);

        List<Map<String, Object>> rows = suggestions.getBody();
        // Commonest first, which is how a member tells the household's tag
        // from the one somebody typed once.
        assertThat(rows.get(0).get("tag")).isEqualTo("italian");
        assertThat(((Number) rows.get(0).get("count")).longValue()).isEqualTo(2L);
    }

    @Test
    void tagSuggestionsNarrowByPrefixAsAMemberTypes() {
        Member ana = givenMember("entry-suggest-prefix");
        String id = newCollection(ana, "Recipes " + ana.username());
        addEntry(ana.session(), id, "{\"name\":\"A\",\"tags\":[\"italian\",\"quick\"]}");

        ResponseEntity<List> suggestions = rest.exchange(
                "/api/data-lists/" + id + "/tags?q=ita", HttpMethod.GET,
                new HttpEntity<>(headersFor(ana.session())), List.class);

        List<Map<String, Object>> rows = suggestions.getBody();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("tag")).isEqualTo("italian");
    }

    @Test
    void tagSuggestionsAreNotReadableWithoutAGrant() {
        Member ana = givenMember("entry-suggest-private");
        Member ben = givenMember("entry-suggest-outsider");
        String id = newCollection(ana, "Private " + ana.username());

        assertThat(rest.exchange("/api/data-lists/" + id + "/tags", HttpMethod.GET,
                new HttpEntity<>(headersFor(ben.session())), Map.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
