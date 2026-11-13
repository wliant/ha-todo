package com.hatodo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Items, against real Postgres.
 *
 * <p>The rules worth proving here are the ones a unit test cannot: that an
 * item on an invisible list is itself invisible, that the second writer on a
 * contended item is told, and that points land on whoever completed it.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
class ItemApiIntegrationTest extends ApiIntegrationTest {

    /** For the handful of rules only the database holds. */
    @Autowired
    private JdbcTemplate jdbc;

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

    private ResponseEntity<Map> setState(Member member, String itemId, long version,
                                         String state) {
        return setState(member, itemId, version, state, null);
    }

    private ResponseEntity<Map> setState(Member member, String itemId, long version,
                                         String state, String noteHtml) {
        return rest.exchange("/api/items/" + itemId + "/state", HttpMethod.PUT,
                new HttpEntity<>("{\"version\":%d,\"state\":\"%s\",\"noteHtml\":%s}"
                        .formatted(version, state,
                                noteHtml == null ? "null" : "\"" + noteHtml + "\""),
                        headersFor(member.session())), Map.class);
    }

    @Test
    void addsAnItemAndListsItBack() {
        Member ana = givenMember("item-owner");
        String listId = newList(ana);

        ResponseEntity<Map> created = addItem(ana, listId, "{\"description\":\"Buy milk\"}");

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().get("description")).isEqualTo("Buy milk");
        assertThat(created.getBody().get("state")).isEqualTo("OPEN");

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

        ResponseEntity<Map> completed = setState(ben, (String) created.get("id"),
                ((Number) created.get("version")).longValue(), "COMPLETED");

        assertThat(completed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(completed.getBody().get("state")).isEqualTo("COMPLETED");
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
        ResponseEntity<Map> completed = setState(ana, (String) created.get("id"),
                ((Number) created.get("version")).longValue(), "COMPLETED");
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

        Map completed = setState(ana, (String) created.get("id"),
                ((Number) created.get("version")).longValue(), "COMPLETED").getBody();
        assertThat(completed.get("pointsEarned")).isEqualTo(1000);

        Map reopened = setState(ana, (String) created.get("id"),
                ((Number) completed.get("version")).longValue(), "OPEN").getBody();
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
        setState(ana, (String) created.get("id"),
                ((Number) created.get("version")).longValue(), "COMPLETED");

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

        assertThat(setState(ben, itemId, bothSee, "COMPLETED").getStatusCode())
                .isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> second = setState(ana, itemId, bothSee, "COMPLETED");

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(second.getBody().get("code")).isEqualTo("STALE_ITEM");
        assertThat((String) second.getBody().get("error")).contains("completed this first");
        // And the body carries the truth, so the client reconciles in one trip.
        Map<String, Object> item = (Map<String, Object>) second.getBody().get("item");
        assertThat(item.get("state")).isEqualTo("COMPLETED");
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

        // The list declares no assignee, so the answer is dropped rather than
        // stored - it would reappear if the field were ever switched on.
        assertThat(created.get("assignee")).isNull();
        // The due date is a different matter since R40: every list declares it,
        // so an answer to it is always kept (adr/0022).
        assertThat(created.get("dueAt")).isEqualTo("2026-12-01T10:00:00Z");
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

    @Test
    void completingWithANoteRecordsHowItWent() {
        Member ana = givenMember("noting");
        String listId = newList(ana);
        Map created = addItem(ana, listId, "{\"description\":\"Fix the tap\"}").getBody();

        Map completed = setState(ana, (String) created.get("id"),
                ((Number) created.get("version")).longValue(), "COMPLETED",
                "<ul><li>Washer was perished</li></ul>").getBody();

        assertThat(completed.get("state")).isEqualTo("COMPLETED");
        assertThat(completed.get("completionNoteHtml"))
                .isEqualTo("<ul><li>Washer was perished</li></ul>");
        assertThat((Map<String, Object>) completed.get("completedBy"))
                .containsEntry("id", ana.id().toString());
        assertThat(completed.get("completedAt")).isNotNull();
    }

    @Test
    void aCompletionNoteIsSanitizedLikeAnyOtherRichText() {
        // Same policy as the detail field, on the same write path (adr/0018,
        // adr/0023). The note is authored by one member and rendered into
        // another member's session, so it is stored filtered or not at all.
        Member ana = givenMember("note-xss");
        String listId = newList(ana);
        Map created = addItem(ana, listId, "{\"description\":\"Risky\"}").getBody();

        Map completed = setState(ana, (String) created.get("id"),
                ((Number) created.get("version")).longValue(), "COMPLETED",
                "<p>Done<script>alert(1)</script></p><img src=\\\"https://evil.test/x.png\\\">")
                .getBody();

        String note = (String) completed.get("completionNoteHtml");
        assertThat(note).contains("Done").doesNotContain("script").doesNotContain("evil.test");
    }

    @Test
    void aNoteSentWhileReopeningIsRefusedRatherThanDropped() {
        // The CHECK would make this a 500, and a field silently discarded
        // hides a client bug for a release (adr/0023).
        Member ana = givenMember("note-on-reopen");
        String listId = newList(ana);
        Map created = addItem(ana, listId, "{\"description\":\"Mow the lawn\"}").getBody();
        Map completed = setState(ana, (String) created.get("id"),
                ((Number) created.get("version")).longValue(), "COMPLETED", null).getBody();

        ResponseEntity<Map> refused = setState(ana, (String) created.get("id"),
                ((Number) completed.get("version")).longValue(), "OPEN", "<p>Actually not</p>");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void reopeningKeepsTheRecordInTheRowAndStopsShowingIt() {
        // R49: the points come back, the rest is kept and simply not reported.
        // Only a real database can show the row still holds it (adr/0024).
        Member ana = givenMember("reopen-keeps");
        String listId = newList(ana);
        Map created = addItem(ana, listId, "{\"description\":\"Hoover\"}").getBody();
        String itemId = (String) created.get("id");
        Map completed = setState(ana, itemId,
                ((Number) created.get("version")).longValue(), "COMPLETED", "<p>Upstairs too</p>")
                .getBody();

        Map reopened = setState(ana, itemId,
                ((Number) completed.get("version")).longValue(), "OPEN").getBody();

        assertThat(reopened.get("state")).isEqualTo("OPEN");
        assertThat(reopened.get("completedBy")).isNull();
        assertThat(reopened.get("completionNoteHtml")).isNull();
        assertThat(reopened.get("reopenedAt")).isNotNull();

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT completed_by, completed_at, completion_note_html, closed "
                        + "FROM todo_item WHERE id = ?::uuid", itemId);
        assertThat(row.get("completed_by")).isNotNull();
        assertThat(row.get("completed_at")).isNotNull();
        assertThat(row.get("completion_note_html")).isEqualTo("<p>Upstairs too</p>");
        assertThat(row.get("closed")).isEqualTo(false);
    }

    @Test
    void lapsingIsTerminalInTheRowAndEarnsNothing() {
        // 0015 R131, R133. The generated `closed` column is the assertion that
        // matters: only a real database computes it, and adr/0033's lesson is
        // that a generation expression Postgres accepts is not necessarily one
        // that computes what was intended.
        Member ana = givenMember("lapse-row");
        String listId = newList(ana);
        Map created = addItem(ana, listId, "{\"description\":\"Never got to it\"}").getBody();
        String itemId = (String) created.get("id");

        Map lapsed = setState(ana, itemId,
                ((Number) created.get("version")).longValue(), "LAPSED").getBody();

        assertThat(lapsed.get("state")).isEqualTo("LAPSED");
        assertThat(lapsed.get("pointsEarned")).isNull();
        assertThat(((Map<String, Object>) lapsed.get("lapsedBy")).get("id"))
                .isEqualTo(ana.id().toString());
        assertThat(lapsed.get("lapsedAt")).isNotNull();

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT lapsed_by, lapsed_at, closed FROM todo_item WHERE id = ?::uuid", itemId);
        assertThat(row.get("lapsed_by")).isNotNull();
        assertThat(row.get("lapsed_at")).isNotNull();
        assertThat(row.get("closed")).isEqualTo(true);
    }

    @Test
    void reopeningALapsedItemMakesItWorkAgainAndKeepsTheLapseInTheRow() {
        // 0015 R135. One reopen stamp undoes either terminal state.
        Member ana = givenMember("lapse-reopen");
        String listId = newList(ana);
        Map created = addItem(ana, listId, "{\"description\":\"Back on the pile\"}").getBody();
        String itemId = (String) created.get("id");
        Map lapsed = setState(ana, itemId,
                ((Number) created.get("version")).longValue(), "LAPSED").getBody();

        Map reopened = setState(ana, itemId,
                ((Number) lapsed.get("version")).longValue(), "OPEN").getBody();

        assertThat(reopened.get("state")).isEqualTo("OPEN");
        assertThat(reopened.get("lapsedBy")).isNull();
        assertThat(reopened.get("lapsedAt")).isNull();

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT lapsed_at, closed FROM todo_item WHERE id = ?::uuid", itemId);
        assertThat(row.get("lapsed_at")).isNotNull();
        assertThat(row.get("closed")).isEqualTo(false);
    }

    @Test
    void anItemCompletedThenReopenedThenLapsedCreditsNobody() {
        // The trap 0015 laid. `closed` is true here and completed_by is still
        // set, so a leaderboard reading `closed = TRUE AND completedBy IS NOT
        // NULL` would invent a zero-point row for Ana. pointsByMember spells
        // out a standing completion instead - see adr/0048.
        Member ana = givenMember("lapse-points");
        String listId = newList(ana);
        configure(ana, listId, """
                [{"kind":"COST","label":"Time","costMode":"FIXED","choices":[
                    {"label":"30 min","points":50}]}]
                """);
        Map<String, Object> cost = costField(ana, listId);
        String choiceId = (String) ((List<Map<String, Object>>) cost.get("choices"))
                .get(0).get("id");
        Map created = addItem(ana, listId, """
                {"description":"Given up on","costValues":[
                    {"fieldId":"%s","choiceId":"%s"}]}
                """.formatted(cost.get("id"), choiceId)).getBody();
        String itemId = (String) created.get("id");

        long version = ((Number) created.get("version")).longValue();
        version = ((Number) setState(ana, itemId, version, "COMPLETED")
                .getBody().get("version")).longValue();
        version = ((Number) setState(ana, itemId, version, "OPEN")
                .getBody().get("version")).longValue();

        setState(ana, itemId, version, "LAPSED");

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT completed_by, closed FROM todo_item WHERE id = ?::uuid", itemId);
        assertThat(row.get("completed_by")).isNotNull();
        assertThat(row.get("closed")).isEqualTo(true);
        assertThat(points(ana, listId)).isEmpty();
    }

    @Test
    void anActivityFeedCallsACompletionUndoneWhenALapseUndidIt() {
        // The flag means "no longer standing", not "carries a reopen stamp" -
        // and a lapse clears that stamp, which is what makes the item terminal
        // again. Reading the column directly reported this completion as still
        // standing, in the one feed whose job is to be honest about work that
        // was undone.
        Member ana = givenMember("lapse-activity");
        String listId = newList(ana);
        Map created = addItem(ana, listId, "{\"description\":\"Undone by a lapse\"}").getBody();
        String itemId = (String) created.get("id");
        long version = ((Number) created.get("version")).longValue();
        version = ((Number) setState(ana, itemId, version, "COMPLETED")
                .getBody().get("version")).longValue();
        version = ((Number) setState(ana, itemId, version, "OPEN")
                .getBody().get("version")).longValue();
        setState(ana, itemId, version, "LAPSED");

        List<Map<String, Object>> events =
                (List<Map<String, Object>>) activity(ana, listId).get("events");

        assertThat(events).hasSize(1);
        assertThat(events.get(0))
                .containsEntry("description", "Undone by a lapse")
                .containsEntry("reopened", true);
    }

    @Test
    void theDatabaseRefusesPointsOnALapsedItem() {
        // A lapsed item is worth nothing, and ck_todo_item_completion says so
        // rather than leaving it to whoever writes the next lapse path.
        Member ana = givenMember("lapse-points-check");
        String listId = newList(ana);
        Map created = addItem(ana, listId, "{\"description\":\"Worthless\"}").getBody();
        String itemId = (String) created.get("id");
        setState(ana, itemId, ((Number) created.get("version")).longValue(), "LAPSED");

        assertThatThrownBy(() -> jdbc.update(
                "UPDATE todo_item SET points_earned = 50 WHERE id = ?::uuid", itemId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void theDatabaseRefusesALapseStampWithoutWhoMadeIt() {
        Member ana = givenMember("lapse-pair-check");
        String listId = newList(ana);
        String itemId = (String) addItem(ana, listId, "{\"description\":\"Half\"}")
                .getBody().get("id");

        assertThatThrownBy(() -> jdbc.update(
                "UPDATE todo_item SET lapsed_at = now() WHERE id = ?::uuid", itemId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void theDatabaseRefusesANoteOnAnItemNobodyCompleted() {
        // Enforced by ck_todo_item_completion, not only by the service: a note
        // about work nobody did is unrepresentable.
        Member ana = givenMember("note-check");
        String listId = newList(ana);
        String itemId = (String) addItem(ana, listId, "{\"description\":\"Open\"}")
                .getBody().get("id");

        assertThatThrownBy(() -> jdbc.update(
                "UPDATE todo_item SET completion_note_html = '<p>x</p>' WHERE id = ?::uuid",
                itemId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void theDatabaseRefusesAReopenThatPredatesItsCompletion() {
        Member ana = givenMember("reopen-check");
        String listId = newList(ana);
        Map created = addItem(ana, listId, "{\"description\":\"Ordered\"}").getBody();
        String itemId = (String) created.get("id");
        setState(ana, itemId, ((Number) created.get("version")).longValue(), "COMPLETED");

        assertThatThrownBy(() -> jdbc.update(
                "UPDATE todo_item SET reopened_at = completed_at - INTERVAL '1 hour', "
                        + "reopened_by = completed_by WHERE id = ?::uuid", itemId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletingAnAccountThatAssignedWorkDoesNotFail() {
        // The test that catches the obvious-looking CHECK pairing assigned_by
        // with assignee_id: both columns are ON DELETE SET NULL, Postgres
        // applies that as an update, and such a CHECK would reject it - which
        // would abort the deletion of exactly those accounts that were used
        // (adr/0026). Nothing here asserts a constraint; it asserts the
        // absence of one.
        Member ana = givenMember("assigner");
        Member ben = givenMember("assignee");
        String listId = newList(ana, ben.id().toString());
        configure(ana, listId, "[{\"kind\":\"ASSIGNEE\"}]");
        addItem(ana, listId, "{\"description\":\"For Ben\",\"assigneeId\":\"%s\"}"
                .formatted(ben.id()));

        Session admin = signIn(ADMIN_USERNAME, ADMIN_PASSWORD);
        ResponseEntity<String> deleted = rest.exchange("/api/admin/users/" + ben.id(),
                HttpMethod.DELETE, new HttpEntity<>(headersFor(admin)), String.class);

        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void claimingWorkForYourselfReadsAsClaimedToEverybody() {
        // R47: a fact about the item, not about who is looking. Ana assigns to
        // Ben and Ben assigns to Ben; both members see the same two answers.
        Member ana = givenMember("claim-owner");
        Member ben = givenMember("claim-peer");
        String listId = newList(ana, ben.id().toString());
        configure(ana, listId, "[{\"kind\":\"ASSIGNEE\"}]");

        Map given = addItem(ana, listId, "{\"description\":\"Given to Ben\",\"assigneeId\":\"%s\"}"
                .formatted(ben.id())).getBody();
        Map taken = addItem(ben, listId, "{\"description\":\"Taken by Ben\",\"assigneeId\":\"%s\"}"
                .formatted(ben.id())).getBody();

        assertThat(given.get("selfAssigned")).isEqualTo(false);
        assertThat(taken.get("selfAssigned")).isEqualTo(true);

        ResponseEntity<List> asAna = rest.exchange("/api/lists/" + listId + "/items",
                HttpMethod.GET, new HttpEntity<>(headersFor(ana.session())), List.class);
        assertThat((List<Map<String, Object>>) asAna.getBody())
                .extracting(item -> item.get("selfAssigned"))
                .containsExactly(false, true);
    }

    @Test
    void anItemReopenedIsBackOnTheToDoList() {
        // The generated `done` column is what the open/done filter reads, so
        // this is the assertion that the derivation actually works end to end.
        Member ana = givenMember("reopen-filter");
        String listId = newList(ana);
        Map created = addItem(ana, listId, "{\"description\":\"Back again\"}").getBody();
        String itemId = (String) created.get("id");
        Map completed = setState(ana, itemId,
                ((Number) created.get("version")).longValue(), "COMPLETED").getBody();

        assertThat(openDescriptions(ana, listId)).isEmpty();

        setState(ana, itemId, ((Number) completed.get("version")).longValue(), "OPEN");

        assertThat(openDescriptions(ana, listId)).containsExactly("Back again");
    }

    /**
     * The open items, picked out here rather than asked for.
     *
     * <p>The endpoint returns the whole list: which part of it a member is
     * shown is decided in the browser (0013, adr/0044). What this still pins is
     * the thing R49 broke once - that `completed` goes false again on a reopen,
     * and that the reopened item comes back rather than staying finished.
     */
    private List<String> openDescriptions(Member member, String listId) {
        ResponseEntity<List> all = rest.exchange("/api/lists/" + listId + "/items",
                HttpMethod.GET, new HttpEntity<>(headersFor(member.session())), List.class);
        return ((List<Map<String, Object>>) all.getBody()).stream()
                .filter(item -> !"COMPLETED".equals(item.get("state")))
                .map(item -> (String) item.get("description"))
                .toList();
    }

    @Test
    void reopeningTakesTheMemberOffTheLeaderboardRatherThanBreakingIt() {
        // The regression R49 created and no unit test could: a reopened item
        // keeps who completed it, so a leaderboard filtered on "has a
        // completer" matched a group whose entire sum was null - a 500 on the
        // points endpoint. Standing credit is `done`, not `completed_by`.
        Member ana = givenMember("points-reopen");
        String listId = newList(ana);
        configure(ana, listId, """
                [{"kind":"COST","label":"Time","costMode":"NUMERIC","costUnitLabel":"hour",
                  "pointsPerUnit":50}]
                """);
        String fieldId = (String) costField(ana, listId).get("id");
        Map created = addItem(ana, listId, """
                {"description":"Worth something","costValues":[{"fieldId":"%s","amount":1}]}
                """.formatted(fieldId)).getBody();

        Map completed = setState(ana, (String) created.get("id"),
                ((Number) created.get("version")).longValue(), "COMPLETED").getBody();
        assertThat(points(ana, listId)).extracting(row -> row.get("points")).containsExactly(50);

        setState(ana, (String) created.get("id"),
                ((Number) completed.get("version")).longValue(), "OPEN");

        // Off the board entirely, and the endpoint still answers.
        assertThat(points(ana, listId)).isEmpty();
    }

    private List<Map<String, Object>> points(Member member, String listId) {
        ResponseEntity<List> response = rest.exchange("/api/lists/" + listId + "/points",
                HttpMethod.GET, new HttpEntity<>(headersFor(member.session())), List.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private Map<String, Object> activity(Member member, String listId) {
        ResponseEntity<Map> response = rest.exchange("/api/lists/" + listId + "/activity",
                HttpMethod.GET, new HttpEntity<>(headersFor(member.session())), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    @Test
    void reportsWhoDidWhatOnThisListLately() {
        // R43: the same facts as the leaderboard, over a window a household
        // can actually feel.
        Member ana = givenMember("activity-owner");
        Member ben = givenMember("activity-peer");
        String listId = newList(ana, ben.id().toString());
        Map mine = addItem(ana, listId, "{\"description\":\"Ana's bit\"}").getBody();
        Map theirs = addItem(ana, listId, "{\"description\":\"Ben's bit\"}").getBody();

        setState(ana, (String) mine.get("id"),
                ((Number) mine.get("version")).longValue(), "COMPLETED");
        setState(ben, (String) theirs.get("id"),
                ((Number) theirs.get("version")).longValue(), "COMPLETED");

        Map<String, Object> activity = activity(ana, listId);

        // By id: the display name is a generated one-off, and the id is what
        // the strip is keyed on anyway.
        assertThat((List<Map<String, Object>>) activity.get("members"))
                .extracting(row -> row.get("userId"))
                .containsExactlyInAnyOrder(ana.id().toString(), ben.id().toString());
        assertThat((List<Map<String, Object>>) activity.get("members"))
                .allSatisfy(row -> assertThat(row).containsEntry("completed", 1));
        assertThat((List<Map<String, Object>>) activity.get("events"))
                .extracting(row -> row.get("description"))
                .containsExactlyInAnyOrder("Ana's bit", "Ben's bit");
        assertThat(activity.get("from")).isNotNull();
        assertThat(activity.get("to")).isNotNull();
    }

    @Test
    void anActivityFeedKeepsACompletionThatWasReopenedSince() {
        // Keyed on completed_at rather than `done`, deliberately: it happened,
        // and R49 kept the record so it could be seen. A week's history that
        // rewrites itself when somebody reopens an item is useless.
        Member ana = givenMember("activity-reopen");
        String listId = newList(ana);
        configure(ana, listId, """
                [{"kind":"COST","label":"Time","costMode":"NUMERIC","costUnitLabel":"hour",
                  "pointsPerUnit":50}]
                """);
        String fieldId = (String) costField(ana, listId).get("id");
        Map created = addItem(ana, listId, """
                {"description":"Undone later","costValues":[{"fieldId":"%s","amount":1}]}
                """.formatted(fieldId)).getBody();
        Map completed = setState(ana, (String) created.get("id"),
                ((Number) created.get("version")).longValue(), "COMPLETED").getBody();

        setState(ana, (String) created.get("id"),
                ((Number) completed.get("version")).longValue(), "OPEN");

        Map<String, Object> activity = activity(ana, listId);
        List<Map<String, Object>> events = (List<Map<String, Object>>) activity.get("events");
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).containsEntry("description", "Undone later")
                .containsEntry("reopened", true);

        // The count still stands; the points went back with the reopen, which
        // is why the sum has to tolerate a null.
        List<Map<String, Object>> members =
                (List<Map<String, Object>>) activity.get("members");
        assertThat(members).hasSize(1);
        assertThat(members.get(0)).containsEntry("completed", 1).containsEntry("points", 0);
    }

    @Test
    void anActivityWindowCanBeAskedForExplicitly() {
        Member ana = givenMember("activity-window");
        String listId = newList(ana);
        Map created = addItem(ana, listId, "{\"description\":\"Just now\"}").getBody();
        setState(ana, (String) created.get("id"),
                ((Number) created.get("version")).longValue(), "COMPLETED");

        // A window that closed before the work happened reports nothing.
        ResponseEntity<Map> past = rest.exchange(
                "/api/lists/" + listId + "/activity?from=2020-01-01T00:00:00Z"
                        + "&to=2020-01-08T00:00:00Z",
                HttpMethod.GET, new HttpEntity<>(headersFor(ana.session())), Map.class);

        assertThat(past.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) past.getBody().get("events")).isEmpty();
        assertThat((List<?>) past.getBody().get("members")).isEmpty();
    }

    @Test
    void aStrangerCannotReadAListsActivity() {
        // R19: the activity of a list you hold no grant on is as invisible as
        // the list itself.
        Member ana = givenMember("activity-private");
        Member stranger = givenMember("activity-stranger");
        String listId = newList(ana);

        ResponseEntity<Map> response = rest.exchange("/api/lists/" + listId + "/activity",
                HttpMethod.GET, new HttpEntity<>(headersFor(stranger.session())), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private ResponseEntity<Map> assign(Member member, String listId, String body) {
        return rest.exchange("/api/lists/" + listId + "/items/assignment", HttpMethod.POST,
                new HttpEntity<>(body, headersFor(member.session())), Map.class);
    }

    @Test
    void claimsSeveralItemsInOneRequest() {
        Member ana = givenMember("bulk-claim");
        String listId = newList(ana);
        configure(ana, listId, "[{\"kind\":\"ASSIGNEE\"}]");
        Map first = addItem(ana, listId, "{\"description\":\"Upstairs\"}").getBody();
        Map second = addItem(ana, listId, "{\"description\":\"Downstairs\"}").getBody();

        ResponseEntity<Map> response = assign(ana, listId, """
                {"assigneeId":"%s","items":[{"id":"%s","version":%d},{"id":"%s","version":%d}]}
                """.formatted(ana.id(), first.get("id"),
                        ((Number) first.get("version")).longValue(), second.get("id"),
                        ((Number) second.get("version")).longValue()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<Map<String, Object>>) response.getBody().get("assigned"))
                .hasSize(2)
                .allSatisfy(item -> assertThat(item).containsEntry("selfAssigned", true));
        assertThat((List<?>) response.getBody().get("conflicts")).isEmpty();
    }

    @Test
    void assignsWhatItCanAndReportsWhatItCouldNot() {
        // R46 with partial success: one item somebody else touched must not
        // discard the member's unambiguous intent for the rest (adr/0026).
        Member ana = givenMember("partial-owner");
        Member ben = givenMember("partial-peer");
        String listId = newList(ana, ben.id().toString());
        configure(ana, listId, "[{\"kind\":\"ASSIGNEE\"}]");
        Map fresh = addItem(ana, listId, "{\"description\":\"Still fresh\"}").getBody();
        Map contended = addItem(ana, listId, "{\"description\":\"Contended\"}").getBody();

        // Ben gets there first, which moves the version Ana is holding.
        setState(ben, (String) contended.get("id"),
                ((Number) contended.get("version")).longValue(), "COMPLETED");

        ResponseEntity<Map> response = assign(ana, listId, """
                {"assigneeId":"%s","items":[{"id":"%s","version":%d},{"id":"%s","version":%d}]}
                """.formatted(ana.id(), fresh.get("id"),
                        ((Number) fresh.get("version")).longValue(), contended.get("id"),
                        ((Number) contended.get("version")).longValue()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<Map<String, Object>>) response.getBody().get("assigned"))
                .extracting(item -> item.get("description")).containsExactly("Still fresh");

        List<Map<String, Object>> conflicts =
                (List<Map<String, Object>>) response.getBody().get("conflicts");
        assertThat(conflicts).hasSize(1);
        assertThat(conflicts.get(0)).containsEntry("id", contended.get("id"));
        assertThat((String) conflicts.get(0).get("error")).contains("completed this first");
        // The server's version of it, so a second attempt retries exactly this
        // one with a version that will work.
        assertThat((Map<String, Object>) conflicts.get(0).get("item"))
                .containsEntry("state", "COMPLETED");
    }

    @Test
    void refusesToAssignToSomebodyWhoCannotSeeTheList() {
        // R24, checked once for the batch and before anything is written.
        Member ana = givenMember("bulk-r24-owner");
        Member stranger = givenMember("bulk-r24-stranger");
        String listId = newList(ana);
        configure(ana, listId, "[{\"kind\":\"ASSIGNEE\"}]");
        Map item = addItem(ana, listId, "{\"description\":\"Not for them\"}").getBody();

        ResponseEntity<Map> response = assign(ana, listId, """
                {"assigneeId":"%s","items":[{"id":"%s","version":%d}]}
                """.formatted(stranger.id(), item.get("id"),
                        ((Number) item.get("version")).longValue()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        // Nothing was written: the check runs before the loop.
        assertThat(rest.exchange("/api/items/" + item.get("id"), HttpMethod.GET,
                new HttpEntity<>(headersFor(ana.session())), Map.class).getBody().get("assignee"))
                .isNull();
    }

    @Test
    void anItemFromAnotherListIsSimplyNoLongerHere() {
        // An id on a list the caller cannot see must answer exactly as a
        // deleted one does, or the response reveals that the list exists (R19).
        Member ana = givenMember("bulk-elsewhere");
        Member ben = givenMember("bulk-elsewhere-peer");
        String mine = newList(ana);
        String theirs = newList(ben);
        configure(ana, mine, "[{\"kind\":\"ASSIGNEE\"}]");
        Map hidden = addItem(ben, theirs, "{\"description\":\"Ben's own\"}").getBody();

        ResponseEntity<Map> response = assign(ana, mine, """
                {"assigneeId":"%s","items":[{"id":"%s","version":%d}]}
                """.formatted(ana.id(), hidden.get("id"),
                        ((Number) hidden.get("version")).longValue()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> conflicts =
                (List<Map<String, Object>>) response.getBody().get("conflicts");
        assertThat(conflicts).hasSize(1);
        assertThat(conflicts.get(0).get("item")).isNull();
        assertThat((String) conflicts.get(0).get("error")).isEqualTo("That item is no longer here.");
    }

    @Test
    void assigningToNobodyClearsTheClaimAsWell() {
        Member ana = givenMember("bulk-unassign");
        String listId = newList(ana);
        configure(ana, listId, "[{\"kind\":\"ASSIGNEE\"}]");
        Map item = addItem(ana, listId, "{\"description\":\"Mine\",\"assigneeId\":\"%s\"}"
                .formatted(ana.id())).getBody();
        assertThat(item.get("selfAssigned")).isEqualTo(true);

        ResponseEntity<Map> response = assign(ana, listId, """
                {"assigneeId":null,"items":[{"id":"%s","version":%d}]}
                """.formatted(item.get("id"), ((Number) item.get("version")).longValue()));

        Map<String, Object> cleared =
                ((List<Map<String, Object>>) response.getBody().get("assigned")).get(0);
        assertThat(cleared.get("assignee")).isNull();
        assertThat(cleared.get("selfAssigned")).isEqualTo(false);
    }

    @Test
    void aStrangerCannotAssignOnAListTheyCannotSee() {
        Member ana = givenMember("bulk-guard-owner");
        Member stranger = givenMember("bulk-guard-stranger");
        String listId = newList(ana);
        Map item = addItem(ana, listId, "{\"description\":\"Private\"}").getBody();

        ResponseEntity<Map> response = assign(stranger, listId, """
                {"assigneeId":null,"items":[{"id":"%s","version":%d}]}
                """.formatted(item.get("id"), ((Number) item.get("version")).longValue()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
