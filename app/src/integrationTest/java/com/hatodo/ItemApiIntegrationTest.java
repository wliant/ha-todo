package com.hatodo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Items, against real Postgres.
 *
 * <p>The rules worth proving here are the ones a unit test cannot: that an
 * item on an invisible list is itself invisible, that the second writer on a
 * contended item is told, and that points land on whoever completed it.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
class ItemApiIntegrationTest extends ApiIntegrationTest {

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
                new HttpEntity<>("{\"name\":\"Items %s\",\"icon\":\"CHECKLIST\",\"memberIds\":%s}"
                        .formatted(UUID.randomUUID(), members), headersFor(owner.session())),
                Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (String) created.getBody().get("id");
    }

    private long listVersion(Member member, String listId) {
        return ((Number) rest.exchange("/api/lists/" + listId, HttpMethod.GET,
                new HttpEntity<>(headersFor(member.session())), Map.class)
                .getBody().get("version")).longValue();
    }

    private void configure(Member owner, String listId, String fields) {
        ResponseEntity<String> response = rest.exchange("/api/lists/" + listId + "/fields",
                HttpMethod.PUT, new HttpEntity<>("{\"version\":%d,\"fields\":%s}"
                        .formatted(listVersion(owner, listId), fields),
                        headersFor(owner.session())), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    /** The list's one cost field, as the API reports it. */
    private Map<String, Object> costField(Member member, String listId) {
        ResponseEntity<List> response = rest.exchange("/api/lists/" + listId + "/fields",
                HttpMethod.GET, new HttpEntity<>(headersFor(member.session())), List.class);
        List<Map<String, Object>> fields = response.getBody();
        return fields.stream()
                .filter(field -> "COST".equals(field.get("kind")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no cost field on " + listId));
    }

    private ResponseEntity<Map> addItem(Member member, String listId, String body) {
        return rest.exchange("/api/lists/" + listId + "/items", HttpMethod.POST,
                new HttpEntity<>(body, headersFor(member.session())), Map.class);
    }

    private ResponseEntity<Map> setCompleted(Member member, String itemId, long version,
                                             boolean completed) {
        return rest.exchange("/api/items/" + itemId + "/completion", HttpMethod.PUT,
                new HttpEntity<>("{\"version\":%d,\"completed\":%s}".formatted(version, completed),
                        headersFor(member.session())), Map.class);
    }

    @Test
    void addsAnItemAndListsItBack() {
        Member ana = givenMember("item-owner");
        String listId = newList(ana);

        ResponseEntity<Map> created = addItem(ana, listId, "{\"description\":\"Buy milk\"}");

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().get("description")).isEqualTo("Buy milk");
        assertThat(created.getBody().get("completed")).isEqualTo(false);

        ResponseEntity<List> listed = rest.exchange("/api/lists/" + listId + "/items",
                HttpMethod.GET, new HttpEntity<>(headersFor(ana.session())), List.class);
        assertThat((List<Map<String, Object>>) listed.getBody())
                .extracting(item -> item.get("description")).containsExactly("Buy milk");
    }

    @Test
    void anItemOnAnInvisibleListIsItselfInvisible() {
        Member ana = givenMember("item-private");
        Member stranger = givenMember("item-stranger");
        String listId = newList(ana);
        String itemId = (String) addItem(ana, listId, "{\"description\":\"Secret\"}")
                .getBody().get("id");

        assertThat(rest.exchange("/api/items/" + itemId, HttpMethod.GET,
                new HttpEntity<>(headersFor(stranger.session())), Map.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(rest.exchange("/api/lists/" + listId + "/items", HttpMethod.GET,
                new HttpEntity<>(headersFor(stranger.session())), String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void everyoneWithAccessIsAPeerOnTheItems() {
        // R30: completion is not the assignee's privilege, nor the owner's.
        Member ana = givenMember("peer-owner");
        Member ben = givenMember("peer-grantee");
        String listId = newList(ana, ben.id().toString());
        Map created = addItem(ana, listId, "{\"description\":\"Shared chore\"}").getBody();

        ResponseEntity<Map> completed = setCompleted(ben, (String) created.get("id"),
                ((Number) created.get("version")).longValue(), true);

        assertThat(completed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(completed.getBody().get("completed")).isEqualTo(true);
    }

    @Test
    void pointsGoToWhoeverCompletedItRatherThanTheAssignee() {
        // The single most mis-implementable rule in the feature (R23).
        Member ana = givenMember("points-owner");
        Member ben = givenMember("points-assignee");
        String listId = newList(ana, ben.id().toString());
        configure(ana, listId, """
                [{"kind":"ASSIGNEE","required":true},
                 {"kind":"COST","label":"Time","costMode":"FIXED","choices":[
                    {"label":"30 min","points":50}]}]
                """);

        Map<String, Object> cost = costField(ana, listId);
        String choiceId = (String) ((List<Map<String, Object>>) cost.get("choices")).get(0).get("id");

        Map created = addItem(ana, listId, """
                {"description":"Assigned to Ben","assigneeId":"%s",
                 "costValues":[{"fieldId":"%s","choiceId":"%s"}]}
                """.formatted(ben.id(), cost.get("id"), choiceId)).getBody();

        // Ana completes an item assigned to Ben.
        ResponseEntity<Map> completed = setCompleted(ana, (String) created.get("id"),
                ((Number) created.get("version")).longValue(), true);
        assertThat(completed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(completed.getBody().get("pointsEarned")).isEqualTo(50);

        ResponseEntity<List> points = rest.exchange("/api/lists/" + listId + "/points",
                HttpMethod.GET, new HttpEntity<>(headersFor(ana.session())), List.class);
        assertThat((List<Map<String, Object>>) points.getBody())
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.get("userId")).isEqualTo(ana.id().toString());
                    assertThat(((Number) row.get("points")).longValue()).isEqualTo(50L);
                });
    }

    @Test
    void reopeningAnItemTakesItsPointsBackAgain() {
        Member ana = givenMember("reopen");
        String listId = newList(ana);
        configure(ana, listId, """
                [{"kind":"COST","label":"Time","costMode":"NUMERIC","costUnitLabel":"hour",
                  "pointsPerUnit":1000}]
                """);
        String fieldId = (String) costField(ana, listId).get("id");

        Map created = addItem(ana, listId, """
                {"description":"An hour of work","costValues":[{"fieldId":"%s","amount":1}]}
                """.formatted(fieldId)).getBody();

        Map completed = setCompleted(ana, (String) created.get("id"),
                ((Number) created.get("version")).longValue(), true).getBody();
        assertThat(completed.get("pointsEarned")).isEqualTo(1000);

        Map reopened = setCompleted(ana, (String) created.get("id"),
                ((Number) completed.get("version")).longValue(), false).getBody();
        assertThat(reopened.get("pointsEarned")).isNull();
        assertThat(reopened.get("completedBy")).isNull();
    }

    @Test
    void editingTheRateLaterDoesNotRewriteWhatSomebodyAlreadyEarned() {
        // The whole reason points are snapshotted (adr/0012).
        Member ana = givenMember("snapshot");
        String listId = newList(ana);
        configure(ana, listId, """
                [{"kind":"COST","label":"Time","costMode":"NUMERIC","costUnitLabel":"hour",
                  "pointsPerUnit":100}]
                """);
        Map<String, Object> cost = costField(ana, listId);

        Map created = addItem(ana, listId, """
                {"description":"An hour","costValues":[{"fieldId":"%s","amount":1}]}
                """.formatted(cost.get("id"))).getBody();
        setCompleted(ana, (String) created.get("id"),
                ((Number) created.get("version")).longValue(), true);

        configure(ana, listId, """
                [{"id":"%s","kind":"COST","label":"Time","costMode":"NUMERIC",
                  "costUnitLabel":"hour","pointsPerUnit":9999}]
                """.formatted(cost.get("id")));

        ResponseEntity<Map> after = rest.exchange("/api/items/" + created.get("id"),
                HttpMethod.GET, new HttpEntity<>(headersFor(ana.session())), Map.class);
        assertThat(after.getBody().get("pointsEarned")).isEqualTo(100);
    }

    @Test
    void theSecondMemberToCompleteAnItemIsToldWhoGotThereFirst() {
        // This is what the whole @Version apparatus is for (R30).
        Member ana = givenMember("race-one");
        Member ben = givenMember("race-two");
        String listId = newList(ana, ben.id().toString());
        Map created = addItem(ana, listId, "{\"description\":\"Contended\"}").getBody();
        String itemId = (String) created.get("id");
        long bothSee = ((Number) created.get("version")).longValue();

        assertThat(setCompleted(ben, itemId, bothSee, true).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> second = setCompleted(ana, itemId, bothSee, true);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(second.getBody().get("code")).isEqualTo("STALE_ITEM");
        assertThat((String) second.getBody().get("error")).contains("completed this first");
        // And the body carries the truth, so the client reconciles in one trip.
        Map<String, Object> item = (Map<String, Object>) second.getBody().get("item");
        assertThat(item.get("completed")).isEqualTo(true);
        assertThat(((Map<String, Object>) item.get("completedBy")).get("id"))
                .isEqualTo(ben.id().toString());
    }

    @Test
    void refusesAnAssigneeWhoCannotSeeTheList() {
        Member ana = givenMember("assign-owner");
        Member outsider = givenMember("assign-outsider");
        String listId = newList(ana);
        configure(ana, listId, "[{\"kind\":\"ASSIGNEE\"}]");

        ResponseEntity<Map> response = addItem(ana, listId,
                "{\"description\":\"Not yours\",\"assigneeId\":\"%s\"}".formatted(outsider.id()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void revokingAccessUnassignsWhatThatPersonWasHolding() {
        Member ana = givenMember("revoke-owner");
        Member ben = givenMember("revoke-assignee");
        String listId = newList(ana, ben.id().toString());
        configure(ana, listId, "[{\"kind\":\"ASSIGNEE\"}]");
        Map created = addItem(ana, listId,
                "{\"description\":\"Ben's job\",\"assigneeId\":\"%s\"}".formatted(ben.id())).getBody();
        assertThat(created.get("assignee")).isNotNull();

        rest.exchange("/api/lists/" + listId + "/access", HttpMethod.PUT,
                new HttpEntity<>("{\"version\":%d,\"memberIds\":[]}".formatted(listVersion(ana, listId)),
                        headersFor(ana.session())), List.class);

        ResponseEntity<Map> after = rest.exchange("/api/items/" + created.get("id"),
                HttpMethod.GET, new HttpEntity<>(headersFor(ana.session())), Map.class);
        assertThat(after.getBody().get("assignee")).isNull();
    }

    @Test
    void refusesAnItemMissingSomethingTheListInsistsOn() {
        Member ana = givenMember("required");
        String listId = newList(ana);
        configure(ana, listId, "[{\"kind\":\"DUE_AT\",\"required\":true}]");

        ResponseEntity<Map> response = addItem(ana, listId, "{\"description\":\"No date\"}");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat((String) response.getBody().get("error")).contains("due date");
    }

    @Test
    void ignoresAnAnswerToAFieldTheListDoesNotAskFor() {
        Member ana = givenMember("unasked");
        String listId = newList(ana);

        Map created = addItem(ana, listId, """
                {"description":"Plain","assigneeId":"%s","dueAt":"2026-12-01T10:00:00Z"}
                """.formatted(ana.id())).getBody();

        assertThat(created.get("assignee")).isNull();
        assertThat(created.get("dueAt")).isNull();
    }

    @Test
    void editingAnItemKeepsTheCostsItAlreadyAnswered() {
        // Re-sending a cost the item already carries must not be an error: the
        // row is matched on its (item, field) key and updated in place.
        Member ana = givenMember("cost-edit");
        String listId = newList(ana);
        configure(ana, listId, """
                [{"kind":"COST","label":"Time","costMode":"NUMERIC","costUnitLabel":"hour",
                  "pointsPerUnit":100}]
                """);
        String fieldId = (String) costField(ana, listId).get("id");

        Map created = addItem(ana, listId, """
                {"description":"An hour","costValues":[{"fieldId":"%s","amount":1}]}
                """.formatted(fieldId)).getBody();

        ResponseEntity<Map> edited = rest.exchange("/api/items/" + created.get("id"),
                HttpMethod.PATCH, new HttpEntity<>("""
                        {"version":%d,"description":"Two hours",
                         "costValues":[{"fieldId":"%s","amount":2}]}
                        """.formatted(((Number) created.get("version")).longValue(), fieldId),
                        headersFor(ana.session())), Map.class);

        assertThat(edited.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(edited.getBody().get("description")).isEqualTo("Two hours");
        assertThat((List<Map<String, Object>>) edited.getBody().get("costValues"))
                .singleElement()
                .satisfies(value ->
                        assertThat(Double.parseDouble(value.get("amount").toString()))
                                .isEqualTo(2.0));
    }

    @Test
    void refusesAStaleEdit() {
        Member ana = givenMember("stale-edit");
        String listId = newList(ana);
        Map created = addItem(ana, listId, "{\"description\":\"First\"}").getBody();
        String itemId = (String) created.get("id");
        long stale = ((Number) created.get("version")).longValue();

        rest.exchange("/api/items/" + itemId, HttpMethod.PATCH,
                new HttpEntity<>("{\"version\":%d,\"description\":\"Second\"}".formatted(stale),
                        headersFor(ana.session())), Map.class);

        ResponseEntity<Map> again = rest.exchange("/api/items/" + itemId, HttpMethod.PATCH,
                new HttpEntity<>("{\"version\":%d,\"description\":\"Third\"}".formatted(stale),
                        headersFor(ana.session())), Map.class);

        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(rest.exchange("/api/items/" + itemId, HttpMethod.GET,
                new HttpEntity<>(headersFor(ana.session())), Map.class).getBody().get("description"))
                .isEqualTo("Second");
    }

    @Test
    void deletingAnItemRemovesIt() {
        Member ana = givenMember("deleter");
        String listId = newList(ana);
        Map created = addItem(ana, listId, "{\"description\":\"Temporary\"}").getBody();

        ResponseEntity<Void> deleted = rest.exchange(
                "/api/items/%s?version=%d".formatted(created.get("id"), created.get("version")),
                HttpMethod.DELETE, new HttpEntity<>(headersFor(ana.session())), Void.class);

        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(rest.exchange("/api/items/" + created.get("id"), HttpMethod.GET,
                new HttpEntity<>(headersFor(ana.session())), Map.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
