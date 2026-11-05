package com.hatodo;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * The dashboard against real Postgres (R44, R45).
 *
 * <p>This is the first read path in the product that does not start from one
 * list, and therefore the first where a missing access guard would leak several
 * members' private lists at once. Most of what follows is that guard, from
 * every angle - because a predicate applied after fetching passes every unit
 * test and leaks every list.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
class DashboardApiIntegrationTest extends ApiIntegrationTest {

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
        String members = memberIds.length == 0 ? "[]" : "[\"" + String.join("\",\"", memberIds) + "\"]";
        ResponseEntity<Map> created = rest.exchange("/api/lists", HttpMethod.POST,
                new HttpEntity<>("{\"name\":\"Dash %s\",\"icon\":\"CHECKLIST\",\"memberIds\":%s}"
                        .formatted(UUID.randomUUID(), members), headersFor(owner.session())),
                Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (String) created.getBody().get("id");
    }

    private Map addItem(Member member, String listId, String body) {
        ResponseEntity<Map> created = rest.exchange("/api/lists/" + listId + "/items",
                HttpMethod.POST, new HttpEntity<>(body, headersFor(member.session())), Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return created.getBody();
    }

    private Map<String, Object> scheduled(Session session, Instant from, Instant to,
                                          String scope) {
        ResponseEntity<Map> response = rest.exchange(
                "/api/dashboard/scheduled?from=%s&to=%s%s".formatted(from, to,
                        scope == null ? "" : "&scope=" + scope),
                HttpMethod.GET, new HttpEntity<>(headersFor(session)), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private List<String> descriptionsDue(Session session, String scope) {
        Instant from = Instant.now().minus(1, ChronoUnit.DAYS);
        Instant to = Instant.now().plus(30, ChronoUnit.DAYS);
        return ((List<Map<String, Object>>) scheduled(session, from, to, scope).get("items"))
                .stream().map(item -> (String) item.get("description")).toList();
    }

    private Map<String, Object> undated(Session session, String scope) {
        ResponseEntity<Map> response = rest.exchange(
                "/api/dashboard/undated" + (scope == null ? "" : "?scope=" + scope),
                HttpMethod.GET, new HttpEntity<>(headersFor(session)), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private static String soon() {
        return Instant.now().plus(1, ChronoUnit.HOURS).toString();
    }

    @Test
    void readsEverythingDueAcrossEveryListTheCallerCanSee() {
        Member ana = givenMember("dash-ana");
        Member ben = givenMember("dash-ben");
        String shared = newList(ana, ben.id().toString());
        String onlyAnas = newList(ana);
        addItem(ana, shared, "{\"description\":\"Shared chore\",\"dueAt\":\"%s\"}".formatted(soon()));
        addItem(ana, onlyAnas, "{\"description\":\"Ana's own\",\"dueAt\":\"%s\"}".formatted(soon()));

        assertThat(descriptionsDue(ana.session(), null))
                .contains("Shared chore", "Ana's own");
        // R19, across lists: Ben holds one grant, so he sees one item.
        assertThat(descriptionsDue(ben.session(), null)).contains("Shared chore");
        assertThat(descriptionsDue(ben.session(), null)).doesNotContain("Ana's own");
    }

    @Test
    void aStrangerSeesNothingOfAnybodyElsesLists() {
        Member ana = givenMember("dash-owner");
        Member stranger = givenMember("dash-stranger");
        String listId = newList(ana);
        addItem(ana, listId, "{\"description\":\"Private chore\",\"dueAt\":\"%s\"}".formatted(soon()));

        assertThat(descriptionsDue(stranger.session(), null)).doesNotContain("Private chore");
        assertThat((List<Map<String, Object>>) undated(stranger.session(), null).get("items"))
                .extracting(item -> item.get("description"))
                .doesNotContain("Private chore");
    }

    @Test
    void theAdminsDashboardHoldsNothingFromAListTheyWereNotGranted() {
        // The test that stops a future "|| isAdmin()". The admin administers
        // accounts, not list contents (adr/0010) - and this is the surface
        // where forgetting that would publish the whole household at once.
        Member ana = givenMember("dash-admin-check");
        String listId = newList(ana);
        addItem(ana, listId, "{\"description\":\"Not the admin's\",\"dueAt\":\"%s\"}".formatted(soon()));

        Session admin = signIn(ADMIN_USERNAME, ADMIN_PASSWORD);

        assertThat(descriptionsDue(admin, null)).doesNotContain("Not the admin's");
    }

    @Test
    void revokingAGrantRemovesTheItemsFromThatMembersDashboard() {
        Member ana = givenMember("dash-revoke-owner");
        Member ben = givenMember("dash-revoke-peer");
        String listId = newList(ana, ben.id().toString());
        addItem(ana, listId, "{\"description\":\"Was Ben's to see\",\"dueAt\":\"%s\"}".formatted(soon()));
        assertThat(descriptionsDue(ben.session(), null)).contains("Was Ben's to see");

        long version = ((Number) rest.exchange("/api/lists/" + listId, HttpMethod.GET,
                new HttpEntity<>(headersFor(ana.session())), Map.class).getBody().get("version"))
                .longValue();
        rest.exchange("/api/lists/" + listId + "/access", HttpMethod.PUT,
                new HttpEntity<>("{\"version\":%d,\"memberIds\":[]}".formatted(version),
                        headersFor(ana.session())), String.class);

        assertThat(descriptionsDue(ben.session(), null)).doesNotContain("Was Ben's to see");
    }

    @Test
    void theWindowIsHalfOpen() {
        Member ana = givenMember("dash-window");
        String listId = newList(ana);
        Instant at = Instant.now().plus(2, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        addItem(ana, listId, "{\"description\":\"On the boundary\",\"dueAt\":\"%s\"}".formatted(at));

        // Inclusive at `from`...
        assertThat((List<Map<String, Object>>) scheduled(ana.session(), at,
                at.plus(1, ChronoUnit.HOURS), null).get("items"))
                .extracting(item -> item.get("description")).contains("On the boundary");
        // ...and exclusive at `to`.
        assertThat((List<Map<String, Object>>) scheduled(ana.session(),
                at.minus(1, ChronoUnit.HOURS), at, null).get("items"))
                .extracting(item -> item.get("description")).doesNotContain("On the boundary");
    }

    @Test
    void aCompletedItemIsNotSomethingLeftToDo() {
        Member ana = givenMember("dash-done");
        String listId = newList(ana);
        Map created = addItem(ana, listId,
                "{\"description\":\"Already finished\",\"dueAt\":\"%s\"}".formatted(soon()));

        rest.exchange("/api/items/" + created.get("id") + "/completion", HttpMethod.PUT,
                new HttpEntity<>("{\"version\":%d,\"completed\":true}"
                        .formatted(((Number) created.get("version")).longValue()),
                        headersFor(ana.session())), Map.class);

        assertThat(descriptionsDue(ana.session(), null)).doesNotContain("Already finished");
    }

    @Test
    void aReopenedItemIsSomethingLeftToDoAgain() {
        // The generated `done` column, read from the far side of the product:
        // a reopened item keeps its completion record and is still outstanding.
        Member ana = givenMember("dash-reopened");
        String listId = newList(ana);
        Map created = addItem(ana, listId,
                "{\"description\":\"Back on the pile\",\"dueAt\":\"%s\"}".formatted(soon()));
        Map completed = rest.exchange("/api/items/" + created.get("id") + "/completion",
                HttpMethod.PUT, new HttpEntity<>("{\"version\":%d,\"completed\":true}"
                        .formatted(((Number) created.get("version")).longValue()),
                        headersFor(ana.session())), Map.class).getBody();

        rest.exchange("/api/items/" + created.get("id") + "/completion", HttpMethod.PUT,
                new HttpEntity<>("{\"version\":%d,\"completed\":false}"
                        .formatted(((Number) completed.get("version")).longValue()),
                        headersFor(ana.session())), Map.class);

        assertThat(descriptionsDue(ana.session(), null)).contains("Back on the pile");
    }

    @Test
    void scopeMineNarrowsToWhatTheCallerIsAssigned() {
        Member ana = givenMember("dash-scope-owner");
        Member ben = givenMember("dash-scope-peer");
        String listId = newList(ana, ben.id().toString());
        long version = ((Number) rest.exchange("/api/lists/" + listId, HttpMethod.GET,
                new HttpEntity<>(headersFor(ana.session())), Map.class).getBody().get("version"))
                .longValue();
        rest.exchange("/api/lists/" + listId + "/fields", HttpMethod.PUT,
                new HttpEntity<>("{\"version\":%d,\"fields\":[{\"kind\":\"ASSIGNEE\"}]}"
                        .formatted(version), headersFor(ana.session())), String.class);

        addItem(ana, listId, "{\"description\":\"For Ben\",\"assigneeId\":\"%s\",\"dueAt\":\"%s\"}"
                .formatted(ben.id(), soon()));
        addItem(ana, listId, "{\"description\":\"For nobody\",\"dueAt\":\"%s\"}".formatted(soon()));

        // Everything readable by default - including the unclaimed chore, which
        // is the whole reason the default is not "mine".
        assertThat(descriptionsDue(ben.session(), null)).contains("For Ben", "For nobody");
        assertThat(descriptionsDue(ben.session(), "mine")).containsExactly("For Ben");
        assertThat(descriptionsDue(ana.session(), "mine")).doesNotContain("For Ben");
    }

    @Test
    void theUndatedPanelHoldsWhatNobodyHasDatedAndCountsTheRest() {
        Member ana = givenMember("dash-undated");
        String listId = newList(ana);
        addItem(ana, listId, "{\"description\":\"No date on this\"}");
        addItem(ana, listId, "{\"description\":\"Dated\",\"dueAt\":\"%s\"}".formatted(soon()));

        Map<String, Object> panel = undated(ana.session(), null);

        assertThat((List<Map<String, Object>>) panel.get("items"))
                .extracting(item -> item.get("description"))
                .contains("No date on this")
                .doesNotContain("Dated");
        assertThat(((Number) panel.get("total")).longValue()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void everyRowSaysWhichListItCameFrom() {
        // The dashboard crosses lists, so a row without its list is unreadable
        // - and the icon is how a list is recognised at a glance (R18).
        Member ana = givenMember("dash-provenance");
        String listId = newList(ana);
        addItem(ana, listId, "{\"description\":\"From somewhere\",\"dueAt\":\"%s\"}".formatted(soon()));

        Map<String, Object> row = ((List<Map<String, Object>>) scheduled(ana.session(),
                Instant.now().minus(1, ChronoUnit.DAYS),
                Instant.now().plus(30, ChronoUnit.DAYS), null).get("items")).stream()
                .filter(item -> "From somewhere".equals(item.get("description")))
                .findFirst().orElseThrow();

        assertThat(row).containsEntry("listId", listId)
                .containsEntry("listIcon", "CHECKLIST");
        assertThat((String) row.get("listName")).startsWith("Dash ");
    }

    @Test
    void anAnonymousCallerIsRefused() {
        ResponseEntity<Map> response = rest.exchange(
                "/api/dashboard/undated", HttpMethod.GET, HttpEntity.EMPTY, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void saysHowFarEitherSideOfTodayEventsHaveBeenReadAtAll() {
        // 0008 R77: My day can now be walked past the window the poller fills,
        // and an empty February must not look like a quiet one. The client
        // cannot hold its own copy of these numbers - they are deployment
        // configuration - so the server reports them.
        Member ana = givenMember("dash-window");
        Instant from = Instant.now();
        Instant to = from.plus(1, ChronoUnit.DAYS);

        Map<String, Object> window =
                (Map<String, Object>) scheduled(ana.session(), from, to, null).get("eventWindow");

        assertThat(window).isNotNull();
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Singapore"));
        assertThat(LocalDate.parse((String) window.get("from")))
                .isEqualTo(today.minusDays(45));
        assertThat(LocalDate.parse((String) window.get("to")))
                .isEqualTo(today.plusDays(90));
    }
}
