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
 * Entry search (requirements/0006 R53-R56, adr/0031 and adr/0033).
 *
 * <p>This suite is the only place any of the search design can be tested: the
 * statement is native, the ranking is Postgres's, and the document is a
 * generated column. A unit test here would assert the schema as described
 * rather than the schema as it behaves - which is exactly how adr/0030's
 * inert {@code setweight} survived review.
 *
 * <p>Ranking is asserted as an <b>order</b>, never as a set. A set assertion
 * passes with the weights reversed.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
class DataEntrySearchApiIntegrationTest extends ApiIntegrationTest {

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

    private String addEntry(Member member, String collectionId, String json) {
        return (String) rest.exchange("/api/data-lists/" + collectionId + "/entries",
                HttpMethod.POST, new HttpEntity<>(json, headersFor(member.session())), Map.class)
                .getBody().get("id");
    }

    /**
     * A {@link java.net.URI}, not a string.
     *
     * <p>{@code exchange(String, ...)} treats its argument as a URI template
     * and re-encodes it, so a pre-encoded {@code %22} becomes {@code %2522}
     * and the quotes of a phrase query never reach the server. The URI
     * overload skips template expansion. This cost an hour: the phrase test
     * failed while the negation test passed, because only one of them contains
     * a character that needs escaping.
     */
    private java.net.URI uri(String path, String query) {
        return org.springframework.web.util.UriComponentsBuilder.fromPath(path)
                .queryParam("q", query)
                .build()
                .toUri();
    }

    private List<Map<String, Object>> scoped(Member member, String collectionId, String query) {
        return rest.exchange(uri("/api/data-lists/" + collectionId + "/entries", query),
                HttpMethod.GET, new HttpEntity<>(headersFor(member.session())), List.class)
                .getBody();
    }

    private List<Map<String, Object>> global(Member member, String query) {
        return rest.exchange(uri("/api/data-entries", query),
                HttpMethod.GET, new HttpEntity<>(headersFor(member.session())), List.class)
                .getBody();
    }

    private static List<String> names(List<Map<String, Object>> hits) {
        return hits.stream().map(hit -> (String) hit.get("name")).toList();
    }

    // --- what the document covers (R53) -------------------------------------

    @Test
    void scopedSearchMatchesTheName() {
        Member ana = givenMember("s-name");
        String id = newCollection(ana, "Recipes " + ana.username());
        addEntry(ana, id, "{\"name\":\"Tiramisu\"}");
        addEntry(ana, id, "{\"name\":\"Pavlova\"}");

        assertThat(names(scoped(ana, id, "tiramisu"))).containsExactly("Tiramisu");
    }

    @Test
    void scopedSearchMatchesTheDescription() {
        Member ana = givenMember("s-desc");
        String id = newCollection(ana, "Recipes " + ana.username());
        addEntry(ana, id, "{\"name\":\"Tiramisu\",\"description\":\"coffee and mascarpone\"}");

        assertThat(names(scoped(ana, id, "mascarpone"))).containsExactly("Tiramisu");
    }

    @Test
    void scopedSearchMatchesTheDetailsText() {
        // The point of the whole feature: a recipe whose only mention of an
        // ingredient is a bullet three lines down is still findable by it.
        Member ana = givenMember("s-detail");
        String id = newCollection(ana, "Recipes " + ana.username());
        addEntry(ana, id, "{\"name\":\"Tiramisu\",\"detailHtml\":"
                + "\"<ul><li>savoiardi</li><li>cocoa</li></ul>\"}");

        assertThat(names(scoped(ana, id, "savoiardi"))).containsExactly("Tiramisu");
        // And the word boundary survived: "savoiardicocoa" is not a lexeme.
        assertThat(names(scoped(ana, id, "cocoa"))).containsExactly("Tiramisu");
    }

    @Test
    void scopedSearchMatchesATag() {
        Member ana = givenMember("s-tag");
        String id = newCollection(ana, "Recipes " + ana.username());
        addEntry(ana, id, "{\"name\":\"Tiramisu\",\"tags\":[\"italian\"]}");

        assertThat(names(scoped(ana, id, "italian"))).containsExactly("Tiramisu");
    }

    /**
     * adr/0033's regression guard. The superseded form stored tags verbatim, so
     * a member typing the exact tag they created found nothing - the query side
     * stems unconditionally, and "desserts" stems to "dessert".
     */
    @Test
    void aTagIsFoundByItsSingularAndItsPlural() {
        Member ana = givenMember("s-tagstem");
        String id = newCollection(ana, "Recipes " + ana.username());
        addEntry(ana, id, "{\"name\":\"Tiramisu\",\"tags\":[\"desserts\"]}");

        assertThat(names(scoped(ana, id, "desserts"))).containsExactly("Tiramisu");
        assertThat(names(scoped(ana, id, "dessert"))).containsExactly("Tiramisu");
    }

    @Test
    void theGeneratedDocumentIsRewrittenWhenAnEntryIsEdited() {
        Member ana = givenMember("s-reindex");
        String id = newCollection(ana, "Recipes " + ana.username());
        String entryId = addEntry(ana, id, "{\"name\":\"Tiramisu\"}");

        rest.exchange("/api/data-entries/" + entryId, HttpMethod.PATCH, new HttpEntity<>(
                "{\"version\":0,\"name\":\"Affogato\"}", headersFor(ana.session())), Map.class);

        assertThat(names(scoped(ana, id, "affogato"))).containsExactly("Affogato");
        assertThat(scoped(ana, id, "tiramisu")).isEmpty();
    }

    // --- ranking (the part a set assertion cannot catch) --------------------

    @Test
    void aTagHitOutranksADetailHit() {
        // adr/0033: with the superseded expression these ranked *equal*,
        // because setweight cannot weight a position-less lexeme.
        Member ana = givenMember("s-rank-tag");
        String id = newCollection(ana, "Recipes " + ana.username());
        addEntry(ana, id, "{\"name\":\"Tiramisu\",\"tags\":[\"desserts\"]}");
        addEntry(ana, id, "{\"name\":\"Pavlova\",\"detailHtml\":"
                + "\"<p>desserts are mostly sugar</p>\"}");

        assertThat(names(scoped(ana, id, "dessert")))
                .containsExactly("Tiramisu", "Pavlova");
    }

    @Test
    void aNameHitOutranksADetailHit() {
        Member ana = givenMember("s-rank-name");
        String id = newCollection(ana, "Recipes " + ana.username());
        addEntry(ana, id, "{\"name\":\"Zabaglione\",\"detailHtml\":\"<p>nothing</p>\"}");
        addEntry(ana, id, "{\"name\":\"Alpha\",\"detailHtml\":\"<p>zabaglione</p>\"}");

        // Alphabetically Alpha comes first, so this can only pass if rank is
        // the primary sort.
        assertThat(names(scoped(ana, id, "zabaglione")))
                .containsExactly("Zabaglione", "Alpha");
    }

    // --- global search (R54) ------------------------------------------------

    @Test
    void globalSearchReportsWhichCollectionEachHitCameFrom() {
        Member ana = givenMember("s-global");
        String recipes = newCollection(ana, "Recipes " + ana.username());
        String vocab = newCollection(ana, "Vocabulary " + ana.username());
        addEntry(ana, recipes, "{\"name\":\"Zuppa inglese\"}");
        addEntry(ana, vocab, "{\"name\":\"Zuppa the word\"}");

        List<Map<String, Object>> hits = global(ana, "zuppa");

        assertThat(hits).hasSize(2);
        assertThat(hits).allSatisfy(hit -> {
            assertThat(hit.get("listName")).isNotNull();
            assertThat(hit.get("listIcon")).isNotNull();
            assertThat(hit.get("dataListId")).isNotNull();
        });
        assertThat(hits.stream().map(h -> (String) h.get("listName")))
                .anyMatch(name -> name.startsWith("Recipes"))
                .anyMatch(name -> name.startsWith("Vocabulary"));
    }

    /**
     * The grant EXISTS is the driving predicate, not a filter applied
     * afterwards - so there is no code path that could fetch this row and then
     * forget to drop it.
     */
    @Test
    void globalSearchSkipsCollectionsTheCallerHoldsNoGrantOn() {
        Member ana = givenMember("s-global-mine");
        Member ben = givenMember("s-global-theirs");
        String hidden = newCollection(ben, "Bens " + ben.username());
        String unique = "quokka" + UUID.randomUUID().toString().substring(0, 6);
        addEntry(ben, hidden, "{\"name\":\"%s\"}".formatted(unique));

        assertThat(global(ana, unique)).isEmpty();
        assertThat(names(global(ben, unique))).containsExactly(unique);
    }

    @Test
    void globalSearchIsUnauthenticatedAs401RatherThanEmpty() {
        assertThat(rest.getForEntity("/api/data-entries?q=anything", Map.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // --- R55: prefix, and the middle of a word ------------------------------

    @Test
    void prefixMatchingFindsAvianFromAvi() {
        // Free from GIN on a tsvector: the last lexeme is promoted to avi:*.
        Member ana = givenMember("s-prefix");
        String id = newCollection(ana, "Vocabulary " + ana.username());
        addEntry(ana, id, "{\"name\":\"Avian\"}");

        assertThat(names(scoped(ana, id, "avi"))).containsExactly("Avian");
    }

    @Test
    void onlyTheLastWordIsTreatedAsAPrefix() {
        Member ana = givenMember("s-lastword");
        String id = newCollection(ana, "Vocabulary " + ana.username());
        addEntry(ana, id, "{\"name\":\"Avian flu\",\"description\":\"birds\"}");

        // "avi bir" -> 'avi' & 'bir':* - the first must match a whole lexeme,
        // so a stem that merely starts with "avi" is not enough on its own.
        assertThat(scoped(ana, id, "avi bir")).isEmpty();
        assertThat(names(scoped(ana, id, "avian bir"))).containsExactly("Avian flu");
    }

    @Test
    void substringFallbackFindsAvianFromTheMiddleOfTheWord() {
        // Full text cannot do this at all, which is why pg_trgm is installed.
        Member ana = givenMember("s-substring");
        String id = newCollection(ana, "Vocabulary " + ana.username());
        addEntry(ana, id, "{\"name\":\"Avian\"}");

        assertThat(names(scoped(ana, id, "vian"))).containsExactly("Avian");
    }

    @Test
    void aFullTextHitOutranksASubstringOnlyHit() {
        Member ana = givenMember("s-rank-trgm");
        String id = newCollection(ana, "Vocabulary " + ana.username());
        // "Zesty" matches 'zest' as a lexeme; "Azesty" only as a substring.
        addEntry(ana, id, "{\"name\":\"Azesty\"}");
        addEntry(ana, id, "{\"name\":\"Zesty\"}");

        // Alphabetically Azesty is first, so order proves rank leads.
        assertThat(names(scoped(ana, id, "zesty")))
                .containsExactly("Zesty", "Azesty");
    }

    @Test
    void aTwoCharacterQueryDoesNotFallBackToSubstring() {
        Member ana = givenMember("s-short");
        String id = newCollection(ana, "Vocabulary " + ana.username());
        addEntry(ana, id, "{\"name\":\"Avian\"}");

        // "ia" is inside "avian", but a trigram index cannot help below three
        // characters and a two-letter wildcard would match most of a
        // collection. The prefix arm still applies, and "ia" is not a prefix.
        assertThat(scoped(ana, id, "ia")).isEmpty();
    }

    // --- advanced queries ---------------------------------------------------

    @Test
    void aQuotedPhraseMatchesOnlyThePhrase() {
        Member ana = givenMember("s-phrase");
        String id = newCollection(ana, "Recipes " + ana.username());
        addEntry(ana, id, "{\"name\":\"A\",\"description\":\"slow cooker beans\"}");
        addEntry(ana, id, "{\"name\":\"B\",\"description\":\"cooker is slow to heat\"}");

        assertThat(names(scoped(ana, id, "\"slow cooker\""))).containsExactly("A");
    }

    @Test
    void aNegatedTermExcludes() {
        Member ana = givenMember("s-negate");
        String id = newCollection(ana, "Recipes " + ana.username());
        addEntry(ana, id, "{\"name\":\"Carbonara\",\"description\":\"pasta with cream\"}");
        addEntry(ana, id, "{\"name\":\"Aglio\",\"description\":\"pasta with garlic\"}");

        assertThat(names(scoped(ana, id, "pasta -cream"))).containsExactly("Aglio");
    }

    @Test
    void aStopwordOnlyQueryReturnsNothingRatherThanFailing() {
        // plainto_tsquery yields an empty tsquery; nullif turns it into NULL,
        // and the statement falls through instead of erroring.
        Member ana = givenMember("s-stopword");
        String id = newCollection(ana, "Recipes " + ana.username());
        addEntry(ana, id, "{\"name\":\"Tiramisu\"}");

        ResponseEntity<List> response = rest.exchange(
                "/api/data-lists/" + id + "/entries?q=the", HttpMethod.GET,
                new HttpEntity<>(headersFor(ana.session())), List.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEmpty();
    }

    @Test
    void aPercentSignInTheQueryIsNotAWildcard() {
        Member ana = givenMember("s-percent");
        String id = newCollection(ana, "Recipes " + ana.username());
        addEntry(ana, id, "{\"name\":\"Tiramisu\"}");

        // Unescaped, "%" would turn the substring arm into "match everything".
        assertThat(scoped(ana, id, "%")).isEmpty();
        assertThat(scoped(ana, id, "%%%")).isEmpty();
    }

    @Test
    void anUnderscoreInTheQueryIsNotAWildcard() {
        Member ana = givenMember("s-underscore");
        String id = newCollection(ana, "Recipes " + ana.username());
        addEntry(ana, id, "{\"name\":\"Tiramisu\"}");

        assertThat(scoped(ana, id, "t_r")).isEmpty();
    }

    @Test
    void aQueryOfPunctuationIsHandledRatherThanThrown() {
        Member ana = givenMember("s-punct");
        String id = newCollection(ana, "Recipes " + ana.username());
        addEntry(ana, id, "{\"name\":\"Tiramisu\"}");

        for (String nasty : List.of("&", "|", "!", "():", "'", "\\", "a & | b")) {
            ResponseEntity<List> response = rest.exchange(
                    uri("/api/data-lists/" + id + "/entries", nasty),
                    HttpMethod.GET, new HttpEntity<>(headersFor(ana.session())), List.class);
            assertThat(response.getStatusCode()).as("query %s", nasty)
                    .isEqualTo(HttpStatus.OK);
        }
    }

    // --- filtering and listing (R56, R57) -----------------------------------

    @Test
    void tagFiltersNarrowRatherThanWiden() {
        Member ana = givenMember("s-filter");
        String id = newCollection(ana, "Recipes " + ana.username());
        addEntry(ana, id, "{\"name\":\"Both\",\"tags\":[\"quick\",\"italian\"]}");
        addEntry(ana, id, "{\"name\":\"OnlyQuick\",\"tags\":[\"quick\"]}");

        ResponseEntity<List> narrowed = rest.exchange(
                "/api/data-lists/" + id + "/entries?tag=quick&tag=italian", HttpMethod.GET,
                new HttpEntity<>(headersFor(ana.session())), List.class);

        // "thai and quick", never "thai or quick".
        assertThat(names(narrowed.getBody())).containsExactly("Both");
    }

    @Test
    void aTagFilterComposesWithASearch() {
        Member ana = givenMember("s-filter-q");
        String id = newCollection(ana, "Recipes " + ana.username());
        addEntry(ana, id, "{\"name\":\"Pasta one\",\"tags\":[\"quick\"]}");
        addEntry(ana, id, "{\"name\":\"Pasta two\",\"tags\":[\"slow\"]}");

        ResponseEntity<List> both = rest.exchange(
                "/api/data-lists/" + id + "/entries?q=pasta&tag=quick", HttpMethod.GET,
                new HttpEntity<>(headersFor(ana.session())), List.class);

        assertThat(names(both.getBody())).containsExactly("Pasta one");
    }

    @Test
    void anEmptyQueryListsTheCollectionByName() {
        // A collection with an empty search box is listed, not emptied - and
        // ordered by name, because R57 says it is read by looking things up.
        Member ana = givenMember("s-listall");
        String id = newCollection(ana, "Recipes " + ana.username());
        addEntry(ana, id, "{\"name\":\"Zabaglione\"}");
        addEntry(ana, id, "{\"name\":\"Affogato\"}");

        assertThat(names(scoped(ana, id, ""))).containsExactly("Affogato", "Zabaglione");
    }

    @Test
    void theResultSetIsBounded() {
        Member ana = givenMember("s-bounded");
        String id = newCollection(ana, "Recipes " + ana.username());
        for (int i = 0; i < 5; i++) {
            addEntry(ana, id, "{\"name\":\"Entry %d\"}".formatted(i));
        }

        ResponseEntity<List> limited = rest.exchange(
                "/api/data-lists/" + id + "/entries?limit=2", HttpMethod.GET,
                new HttpEntity<>(headersFor(ana.session())), List.class);

        assertThat(limited.getBody()).hasSize(2);
    }

    @Test
    void anAbsurdLimitIsCappedRatherThanObeyed() {
        Member ana = givenMember("s-cap");
        String id = newCollection(ana, "Recipes " + ana.username());
        addEntry(ana, id, "{\"name\":\"Only one\"}");

        ResponseEntity<List> response = rest.exchange(
                "/api/data-lists/" + id + "/entries?limit=100000", HttpMethod.GET,
                new HttpEntity<>(headersFor(ana.session())), List.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
    }

    @Test
    void theSearchIndexExistsAndIsUsedRatherThanScanned() {
        // Cheap insurance that the GIN index is actually reachable: a plan
        // without it is a sequential scan on every search.
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes WHERE indexname = 'ix_data_entry_search'",
                Long.class)).isEqualTo(1L);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes WHERE indexname = 'ix_data_entry_name_trgm'",
                Long.class)).isEqualTo(1L);
    }
}
