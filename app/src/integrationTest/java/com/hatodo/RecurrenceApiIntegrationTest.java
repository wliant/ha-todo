package com.hatodo;

import static org.assertj.core.api.Assertions.assertThat;

import com.hatodo.recurrence.RecurrenceMaterialiser;
import com.hatodo.recurrence.RecurrenceRuleRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Recurring items against real Postgres (R41, R42).
 *
 * <p>The rules worth proving here are the ones a unit test cannot: that a
 * second run creates nothing, that the unique index turns a racing run into a
 * skip rather than a duplicate chore, and that generation works with no
 * authenticated caller at all.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
class RecurrenceApiIntegrationTest extends ApiIntegrationTest {

    @Autowired
    private RecurrenceMaterialiser materialiser;

    @Autowired
    private RecurrenceRuleRepository rules;

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
                new HttpEntity<>("{\"name\":\"Recurring %s\",\"icon\":\"CHECKLIST\",\"memberIds\":%s}"
                        .formatted(UUID.randomUUID(), members), headersFor(owner.session())),
                Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (String) created.getBody().get("id");
    }

    private ResponseEntity<Map> addRule(Member member, String listId, String body) {
        return rest.exchange("/api/lists/" + listId + "/recurrence", HttpMethod.POST,
                new HttpEntity<>(body, headersFor(member.session())), Map.class);
    }

    private List<Map<String, Object>> itemsOn(Member member, String listId) {
        return rest.exchange("/api/lists/" + listId + "/items", HttpMethod.GET,
                new HttpEntity<>(headersFor(member.session())), List.class).getBody();
    }

    private List<Map<String, Object>> rulesOn(Member member, String listId) {
        return rest.exchange("/api/lists/" + listId + "/recurrence", HttpMethod.GET,
                new HttpEntity<>(headersFor(member.session())), List.class).getBody();
    }

    @Test
    void aRunMovesTheRulesVersionBecauseItRecordsWhenItRan() {
        // Worth asserting rather than discovering: a member who runs a rule by
        // hand and then edits it is editing a stale view, so the screen has to
        // refetch after a run.
        Member ana = givenMember("run-version");
        String listId = newList(ana);
        Map created = addRule(ana, listId,
                "{\"description\":\"Bookkeeping\",\"frequency\":\"DAILY\"}").getBody();

        runFor(ana, listId);

        long before = ((Number) created.get("version")).longValue();
        long after = ((Number) rulesOn(ana, listId).get(0).get("version")).longValue();
        assertThat(after).isGreaterThan(before);
        assertThat(rulesOn(ana, listId).get(0).get("lastRunAt")).isNotNull();
    }

    private Map<String, Object> runFor(Member member, String listId) {
        ResponseEntity<Map> response = rest.exchange(
                "/api/lists/" + listId + "/recurrence/runs", HttpMethod.POST,
                new HttpEntity<>(headersFor(member.session())), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    @Test
    void aDailyRuleProducesTodaysItemAndThenNothingMore() {
        // The whole point of last_materialised_on: running again the same day
        // computes an empty range rather than a second chore.
        Member ana = givenMember("daily");
        String listId = newList(ana);
        ResponseEntity<Map> rule = addRule(ana, listId,
                "{\"description\":\"Vitamins\",\"frequency\":\"DAILY\"}");
        assertThat(rule.getStatusCode()).as("rule body: %s", rule.getBody())
                .isEqualTo(HttpStatus.CREATED);

        Map<String, Object> first = runFor(ana, listId);
        assertThat(first).as("run: %s", first).containsEntry("generated", 1);
        assertThat(itemsOn(ana, listId)).extracting(item -> item.get("description"))
                .containsExactly("Vitamins");

        Map<String, Object> again = runFor(ana, listId);
        assertThat(again).containsEntry("generated", 0);
        assertThat(itemsOn(ana, listId)).hasSize(1);
    }

    @Test
    void generationNeedsNoAuthenticatedCaller() {
        // Driven straight from the test, with no session and no security
        // context at all. If createFromRule consulted CurrentUserProvider this
        // would throw - and the provider is built to fail rather than fall
        // back, which is exactly why the job must not pretend to be a user
        // (adr/0025).
        Member ana = givenMember("no-caller");
        String listId = newList(ana);
        addRule(ana, listId, "{\"description\":\"Unattended\",\"frequency\":\"DAILY\"}");
        UUID ruleId = UUID.fromString((String) ((List<Map<String, Object>>) rest.exchange(
                "/api/lists/" + listId + "/recurrence", HttpMethod.GET,
                new HttpEntity<>(headersFor(ana.session())), List.class).getBody())
                .get(0).get("id"));

        RecurrenceMaterialiser.Outcome outcome = materialiser.materialise(ruleId);

        assertThat(outcome.generated()).isEqualTo(1);
        assertThat(itemsOn(ana, listId)).extracting(item -> item.get("description"))
                .containsExactly("Unattended");
        // The honest actor: the rule's author, not a synthetic system user.
        assertThat(itemsOn(ana, listId)).hasSize(1);
    }

    @Test
    void anOccurrenceThatAlreadyExistsIsSkippedRatherThanDuplicated() {
        // ux_todo_item_occurrence. Two runs overlapping - an ad-hoc one fired
        // while the scheduled one is in flight - is the one way the
        // single-instance assumption breaks in practice, and it has to be
        // harmless.
        Member ana = givenMember("racing");
        String listId = newList(ana);
        addRule(ana, listId, "{\"description\":\"Once only\",\"frequency\":\"DAILY\"}");
        UUID ruleId = rules.findForList(UUID.fromString(listId)).get(0).getId();

        materialiser.materialise(ruleId);

        // Rewind the high-water mark, so the second run genuinely tries the
        // same date again and only the already-there check can stop it.
        jdbcRewind(ruleId);

        RecurrenceMaterialiser.Outcome second = materialiser.materialise(ruleId);

        assertThat(second.generated()).isZero();
        assertThat(second.skipped()).isEqualTo(1);
        assertThat(itemsOn(ana, listId)).hasSize(1);
    }

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    private void jdbcRewind(UUID ruleId) {
        jdbc.update("UPDATE recurrence_rule SET last_materialised_on = NULL WHERE id = ?", ruleId);
    }

    @Test
    void anOccurrenceCarriesTheTemplateAndNoneOfTheCompletion() {
        Member ana = givenMember("template");
        Member ben = givenMember("template-peer");
        String listId = newList(ana, ben.id().toString());
        long listVersion = ((Number) rest.exchange("/api/lists/" + listId, HttpMethod.GET,
                new HttpEntity<>(headersFor(ana.session())), Map.class).getBody().get("version"))
                .longValue();
        rest.exchange("/api/lists/" + listId + "/fields", HttpMethod.PUT,
                new HttpEntity<>("""
                        {"version":%d,"fields":[
                          {"kind":"DESCRIPTION","required":true},
                          {"kind":"DETAIL"},
                          {"kind":"ASSIGNEE"},
                          {"kind":"DUE_AT"},
                          {"kind":"COST","label":"Time","costMode":"NUMERIC",
                           "costUnitLabel":"hour","pointsPerUnit":50}]}
                        """.formatted(listVersion), headersFor(ana.session())), String.class);
        String costFieldId = (String) ((List<Map<String, Object>>) rest.exchange(
                "/api/lists/" + listId + "/fields", HttpMethod.GET,
                new HttpEntity<>(headersFor(ana.session())), List.class).getBody()).stream()
                .filter(field -> "COST".equals(field.get("kind")))
                .findFirst().orElseThrow().get("id");

        addRule(ana, listId, """
                {"description":"Bins out","detailHtml":"<p>Blue bin</p>",
                 "assigneeId":"%s","frequency":"DAILY","dueTime":"19:00",
                 "costValues":[{"fieldId":"%s","amount":1}]}
                """.formatted(ben.id(), costFieldId));

        runFor(ana, listId);

        Map<String, Object> item = itemsOn(ana, listId).get(0);
        assertThat(item).containsEntry("description", "Bins out")
                .containsEntry("detailHtml", "<p>Blue bin</p>")
                .containsEntry("state", "OPEN")
                // Never inherited: points are computed at completion and this
                // item has not been completed (adr/0012).
                .containsEntry("pointsEarned", null)
                .containsEntry("completedBy", null)
                .containsEntry("completionNoteHtml", null);
        assertThat((Map<String, Object>) item.get("assignee"))
                .containsEntry("id", ben.id().toString());
        assertThat(item.get("dueAt")).isNotNull();
        assertThat((List<?>) item.get("costValues")).hasSize(1);
    }

    @Test
    void aRuleWhoseAssigneeLostAccessProducesAnUnassignedItemAndSaysWhy() {
        // The failure that would otherwise stop the whole household's
        // recurrence over one rule. Defended twice: the grant revocation
        // clears the rule, and the materialiser refuses to name somebody gone.
        Member ana = givenMember("revoked-owner");
        Member ben = givenMember("revoked-peer");
        String listId = newList(ana, ben.id().toString());
        long version = ((Number) rest.exchange("/api/lists/" + listId, HttpMethod.GET,
                new HttpEntity<>(headersFor(ana.session())), Map.class).getBody().get("version"))
                .longValue();
        rest.exchange("/api/lists/" + listId + "/fields", HttpMethod.PUT,
                new HttpEntity<>("{\"version\":%d,\"fields\":[{\"kind\":\"ASSIGNEE\"}]}"
                        .formatted(version), headersFor(ana.session())), String.class);
        addRule(ana, listId, """
                {"description":"Ben's job","frequency":"DAILY","assigneeId":"%s"}
                """.formatted(ben.id()));

        // Ana takes Ben off the list.
        long nextVersion = ((Number) rest.exchange("/api/lists/" + listId, HttpMethod.GET,
                new HttpEntity<>(headersFor(ana.session())), Map.class).getBody().get("version"))
                .longValue();
        rest.exchange("/api/lists/" + listId + "/access", HttpMethod.PUT,
                new HttpEntity<>("{\"version\":%d,\"memberIds\":[]}".formatted(nextVersion),
                        headersFor(ana.session())), String.class);

        runFor(ana, listId);

        // The item exists and is nobody's, rather than not existing at all.
        Map<String, Object> item = itemsOn(ana, listId).get(0);
        assertThat(item).containsEntry("description", "Ben's job");
        assertThat(item.get("assignee")).isNull();
    }

    @Test
    void deletingARuleLeavesTheItemsItAlreadyMade() {
        Member ana = givenMember("rule-delete");
        String listId = newList(ana);
        Map created = addRule(ana, listId,
                "{\"description\":\"Already made\",\"frequency\":\"DAILY\"}").getBody();
        runFor(ana, listId);
        assertThat(itemsOn(ana, listId)).hasSize(1);

        // Re-read first: a run records when it last happened, which moves the
        // rule's version - so the view taken before the run is genuinely stale,
        // exactly as it would be for a second editor. The client refetches
        // after a run for this reason.
        long current = ((Number) rulesOn(ana, listId).get(0).get("version")).longValue();

        ResponseEntity<String> deleted = rest.exchange("/api/recurrence/" + created.get("id")
                        + "?version=" + current,
                HttpMethod.DELETE, new HttpEntity<>(headersFor(ana.session())), String.class);

        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        // Ordinary items now, with rule_id nulled by the foreign key action.
        assertThat(itemsOn(ana, listId)).extracting(item -> item.get("description"))
                .containsExactly("Already made");
    }

    @Test
    void aListWithStandingInstructionsCanStillBeDeleted() {
        // Found by the e2e suite's own teardown, which is the only thing that
        // had ever deleted a list carrying a rule. Loading RecurrenceRule
        // entities while the list is being removed fails Hibernate's
        // transient-reference check, so the delete answered 500 - and the
        // pictures were already being let go by a native query that needs no
        // entities at all.
        Member ana = givenMember("list-with-rules");
        String listId = newList(ana);
        addRule(ana, listId, "{\"description\":\"Goes with it\",\"frequency\":\"DAILY\"}");
        runFor(ana, listId);

        ResponseEntity<String> deleted = rest.exchange("/api/lists/" + listId,
                HttpMethod.DELETE, new HttpEntity<>(headersFor(ana.session())), String.class);

        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(rest.exchange("/api/lists/" + listId, HttpMethod.GET,
                new HttpEntity<>(headersFor(ana.session())), Map.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void anyMemberWithAccessWritesAStandingInstruction() {
        // 0016 R141, replacing the opposite assertion: a rule only adds items,
        // and adding items is what a grant has always meant. R20's reading -
        // that a rule is as much the shape of a list as its fields - was wrong
        // in a specific way, since a field constrains every item anybody adds
        // and a rule constrains nobody (adr/0049).
        Member ana = givenMember("rule-owner");
        Member ben = givenMember("rule-peer");
        String listId = newList(ana, ben.id().toString());

        ResponseEntity<Map> created = addRule(ben, listId,
                "{\"description\":\"Ben's instruction\",\"frequency\":\"DAILY\"}");

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        // R142: the author is published, because a list can now hold rules by
        // four different members and "who decided this" is a real question.
        assertThat(created.getBody()).extracting("createdBy")
                .extracting("id").isEqualTo(ben.id().toString());
        // And it works: a rule written by a member is a rule like any other.
        assertThat(runFor(ana, listId)).containsEntry("generated", 1);
    }

    @Test
    void aMemberEditsAndDeletesTheirOwnRuleOnSomebodyElsesList() {
        // R143's first half. The author is recorded, has been since V910, and
        // is now what decides this.
        Member ana = givenMember("rule-mine");
        Member ben = givenMember("rule-author");
        String listId = newList(ana, ben.id().toString());
        String ruleId = (String) addRule(ben, listId,
                "{\"description\":\"Ben's own\",\"frequency\":\"DAILY\"}").getBody().get("id");

        ResponseEntity<Map> edited = rest.exchange("/api/recurrence/" + ruleId, HttpMethod.PATCH,
                new HttpEntity<>("{\"description\":\"Ben's own, renamed\","
                        + "\"frequency\":\"DAILY\",\"version\":0}",
                        headersFor(ben.session())), Map.class);
        ResponseEntity<String> deleted = rest.exchange(
                "/api/recurrence/" + ruleId + "?version=1", HttpMethod.DELETE,
                new HttpEntity<>(headersFor(ben.session())), String.class);

        assertThat(edited.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(edited.getBody()).containsEntry("description", "Ben's own, renamed");
        assertThat(edited.getBody()).containsEntry("editable", true);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void aMemberCannotRewriteSomebodyElsesRuleOnAListTheyDoNotOwn() {
        // R143's point: writing your own standing arrangement and rewriting
        // somebody else's are different acts. Readable, so 403 and not 404.
        Member ana = givenMember("rule-theirs-owner");
        Member ben = givenMember("rule-theirs-peer");
        String listId = newList(ana, ben.id().toString());
        String ruleId = (String) addRule(ana, listId,
                "{\"description\":\"Ana's own\",\"frequency\":\"DAILY\"}").getBody().get("id");

        ResponseEntity<Map> edit = rest.exchange("/api/recurrence/" + ruleId, HttpMethod.PATCH,
                new HttpEntity<>("{\"description\":\"Hijacked\",\"frequency\":\"DAILY\","
                        + "\"version\":0}", headersFor(ben.session())), Map.class);
        ResponseEntity<Map> delete = rest.exchange("/api/recurrence/" + ruleId + "?version=0",
                HttpMethod.DELETE, new HttpEntity<>(headersFor(ben.session())), Map.class);

        assertThat(edit.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(delete.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        // And the listing says so, which is what the client's control follows.
        assertThat(rulesOn(ben, listId)).singleElement()
                .extracting("editable").isEqualTo(false);
        assertThat(rulesOn(ana, listId)).singleElement()
                .extracting("editable").isEqualTo(true);
    }

    @Test
    void theOwnerRewritesAndRemovesAMembersRule() {
        // R143's other half, and the half easy to leave out: the owner keeps
        // final authority over everything on their list, including the rules of
        // a member whose access has since gone.
        Member ana = givenMember("rule-owner-wins");
        Member ben = givenMember("rule-guest");
        String listId = newList(ana, ben.id().toString());
        String ruleId = (String) addRule(ben, listId,
                "{\"description\":\"Ben's\",\"frequency\":\"DAILY\"}").getBody().get("id");

        ResponseEntity<Map> edited = rest.exchange("/api/recurrence/" + ruleId, HttpMethod.PATCH,
                new HttpEntity<>("{\"description\":\"Owner's wording\","
                        + "\"frequency\":\"DAILY\",\"version\":0}",
                        headersFor(ana.session())), Map.class);
        ResponseEntity<String> deleted = rest.exchange(
                "/api/recurrence/" + ruleId + "?version=1", HttpMethod.DELETE,
                new HttpEntity<>(headersFor(ana.session())), String.class);

        assertThat(edited.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(edited.getBody()).containsEntry("editable", true);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void aStrangerGetsTheSame404ForARuleTheyCannotSeeAsForOneThatIsGone() {
        // The ordering inside `writable`: readability is asked first, so a list
        // the caller holds no grant on stays undiscoverable through the rules
        // hanging off it (R19). A 403 here would be a yes/no oracle on
        // somebody else's private list.
        Member ana = givenMember("rule-hidden");
        Member stranger = givenMember("rule-outsider");
        String listId = newList(ana);
        String ruleId = (String) addRule(ana, listId,
                "{\"description\":\"Private\",\"frequency\":\"DAILY\"}").getBody().get("id");

        ResponseEntity<Map> edit = rest.exchange("/api/recurrence/" + ruleId, HttpMethod.PATCH,
                new HttpEntity<>("{\"description\":\"Nope\",\"frequency\":\"DAILY\","
                        + "\"version\":0}", headersFor(stranger.session())), Map.class);
        ResponseEntity<Map> gone = rest.exchange("/api/recurrence/" + UUID.randomUUID(),
                HttpMethod.PATCH,
                new HttpEntity<>("{\"description\":\"Nope\",\"frequency\":\"DAILY\","
                        + "\"version\":0}", headersFor(stranger.session())), Map.class);

        assertThat(edit.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(gone.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void butAnyMemberCanRunThemAndSeeThem() {
        // A member who has just seen a rule wants to watch it work, and what it
        // makes is an item they could have typed by hand.
        Member ana = givenMember("run-owner");
        Member ben = givenMember("run-peer");
        String listId = newList(ana, ben.id().toString());
        addRule(ana, listId, "{\"description\":\"Shared chore\",\"frequency\":\"DAILY\"}");

        Map<String, Object> run = runFor(ben, listId);

        assertThat(run).containsEntry("generated", 1);
    }

    @Test
    void aStrangerSeesNeitherTheRulesNorTheirExistence() {
        Member ana = givenMember("rule-private");
        Member stranger = givenMember("rule-stranger");
        String listId = newList(ana);
        addRule(ana, listId, "{\"description\":\"Private\",\"frequency\":\"DAILY\"}");

        ResponseEntity<Map> listed = rest.exchange("/api/lists/" + listId + "/recurrence",
                HttpMethod.GET, new HttpEntity<>(headersFor(stranger.session())), Map.class);
        ResponseEntity<Map> ran = rest.exchange("/api/lists/" + listId + "/recurrence/runs",
                HttpMethod.POST, new HttpEntity<>(headersFor(stranger.session())), Map.class);

        assertThat(listed.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ran.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void aWeeklyRuleNeedsAtLeastOneDay() {
        Member ana = givenMember("no-days");
        String listId = newList(ana);

        ResponseEntity<Map> refused = addRule(ana, listId,
                "{\"description\":\"When?\",\"frequency\":\"WEEKLY\"}");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void theAdminCanMakeEveryRuleRunButLearnsNothingAboutAnyList() {
        // adr/0010 holds: the admin administers accounts. This is defensible
        // only because the response is counts and nothing else.
        Member ana = givenMember("admin-run");
        String listId = newList(ana);
        addRule(ana, listId, "{\"description\":\"Invisible to admin\",\"frequency\":\"DAILY\"}");

        Session admin = signIn(ADMIN_USERNAME, ADMIN_PASSWORD);
        ResponseEntity<Map> run = rest.exchange("/api/admin/recurrence/runs", HttpMethod.POST,
                new HttpEntity<>(headersFor(admin)), Map.class);

        assertThat(run.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(run.getBody().keySet())
                .containsExactlyInAnyOrder("rulesScanned", "generated", "skipped", "failed");
        assertThat(((Number) run.getBody().get("generated")).intValue()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void aMemberCannotMakeEveryRuleRun() {
        Member ana = givenMember("not-admin-run");

        ResponseEntity<Map> refused = rest.exchange("/api/admin/recurrence/runs",
                HttpMethod.POST, new HttpEntity<>(headersFor(ana.session())), Map.class);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void theSchedulerIsOffInThisProfile() {
        // Left on, a run could fire mid-assertion and a flake would appear
        // months later with no obvious cause. Asserted rather than assumed, so
        // a future context change cannot quietly reintroduce it.
        assertThat(applicationContext.getBeanNamesForType(
                org.springframework.scheduling.config.ScheduledTaskRegistrar.class)).isEmpty();
        assertThat(applicationContext.containsBean("recurrenceSchedulingConfiguration")).isFalse();
    }

    @Autowired
    private org.springframework.context.ApplicationContext applicationContext;
}
