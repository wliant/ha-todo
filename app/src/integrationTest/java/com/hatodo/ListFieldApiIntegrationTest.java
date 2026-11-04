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
 * What shape a list's items take, against real Postgres.
 *
 * <p>Several of these exist only because a real database says no where an
 * in-memory one would not: the deferred position constraints, the partial
 * unique indexes, and the merge that has to preserve ids rather than recreate
 * rows.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
class ListFieldApiIntegrationTest extends ApiIntegrationTest {

    private record Owner(UUID id, Session session) {}

    private Owner givenOwner(String label) {
        String username = label + "-" + UUID.randomUUID().toString().substring(0, 8);
        Session admin = signIn(ADMIN_USERNAME, ADMIN_PASSWORD);
        ResponseEntity<Map> created = rest.exchange("/api/admin/users", HttpMethod.POST,
                new HttpEntity<>("{\"username\":\"%s\",\"displayName\":\"%s\",\"password\":\"pw\"}"
                        .formatted(username, username), headersFor(admin)), Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return new Owner(UUID.fromString((String) created.getBody().get("id")),
                signIn(username, "pw"));
    }

    private Map newList(Owner owner) {
        ResponseEntity<Map> created = rest.exchange("/api/lists", HttpMethod.POST,
                new HttpEntity<>("{\"name\":\"Fields %s\",\"icon\":\"CHECKLIST\"}"
                        .formatted(UUID.randomUUID()), headersFor(owner.session())), Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return created.getBody();
    }

    /**
     * Returns the raw body: a refusal is an object and a success is an array,
     * so anything narrower fails to parse exactly when the test is about the
     * refusal.
     */
    private ResponseEntity<String> putFields(Owner owner, String listId, long version, String fields) {
        return rest.exchange("/api/lists/" + listId + "/fields", HttpMethod.PUT,
                new HttpEntity<>("{\"version\":%d,\"fields\":%s}".formatted(version, fields),
                        headersFor(owner.session())), String.class);
    }

    private List<Map<String, Object>> getFields(Owner owner, String listId) {
        return rest.exchange("/api/lists/" + listId + "/fields", HttpMethod.GET,
                new HttpEntity<>(headersFor(owner.session())), List.class).getBody();
    }

    private long versionOf(Owner owner, String listId) {
        return ((Number) rest.exchange("/api/lists/" + listId, HttpMethod.GET,
                new HttpEntity<>(headersFor(owner.session())), Map.class)
                .getBody().get("version")).longValue();
    }

    @Test
    void everyNewListAlreadyHasARequiredDescription() {
        Owner ana = givenOwner("fresh");
        String listId = (String) newList(ana).get("id");

        List<Map<String, Object>> fields = getFields(ana, listId);

        assertThat(fields).hasSize(1);
        assertThat(fields.get(0)).containsEntry("kind", "DESCRIPTION").containsEntry("required", true);
    }

    @Test
    void keepsTheDescriptionEvenWhenTheClientForgetsToSendIt() {
        // Items will always have one, so a configuration without it is not a
        // state the API should let anyone reach.
        Owner ana = givenOwner("forgetful");
        String listId = (String) newList(ana).get("id");

        putFields(ana, listId, versionOf(ana, listId), "[{\"kind\":\"DUE_AT\",\"required\":false}]");

        assertThat(getFields(ana, listId)).extracting(field -> field.get("kind"))
                .containsExactly("DESCRIPTION", "DUE_AT");
    }

    @Test
    void refusesToMakeTheDescriptionOptional() {
        Owner ana = givenOwner("optional-description");
        String listId = (String) newList(ana).get("id");

        putFields(ana, listId, versionOf(ana, listId),
                "[{\"kind\":\"DESCRIPTION\",\"required\":false}]");

        assertThat(getFields(ana, listId).get(0)).containsEntry("required", true);
    }

    @Test
    void declaresSeveralNamedCostsOnOneList() {
        // R22: a chore costs both time and money, and those are different
        // questions with different units.
        Owner ana = givenOwner("two-costs");
        String listId = (String) newList(ana).get("id");

        ResponseEntity<String> saved = putFields(ana, listId, versionOf(ana, listId), """
                [
                  {"kind":"DESCRIPTION","required":true},
                  {"kind":"COST","label":"Time","costMode":"FIXED","choices":[
                     {"label":"5 min","points":10},
                     {"label":"30 min","points":50},
                     {"label":"60 min","points":120}]},
                  {"kind":"COST","label":"Money","costMode":"NUMERIC",
                   "costUnitLabel":"dollar","pointsPerUnit":1}
                ]
                """);

        assertThat(saved.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> fields = getFields(ana, listId);
        assertThat(fields).extracting(field -> field.get("label"))
                .containsExactly(null, "Time", "Money");
        assertThat((List<Map<String, Object>>) fields.get(1).get("choices"))
                .extracting(choice -> choice.get("label"))
                .containsExactly("5 min", "30 min", "60 min");
        assertThat(fields.get(2)).containsEntry("costUnitLabel", "dollar");
    }

    @Test
    void refusesTwoCostsWithTheSameName() {
        Owner ana = givenOwner("clashing-costs");
        String listId = (String) newList(ana).get("id");

        ResponseEntity<String> response = putFields(ana, listId, versionOf(ana, listId), """
                [{"kind":"COST","label":"Time","costMode":"NUMERIC","costUnitLabel":"hour"},
                 {"kind":"COST","label":"time","costMode":"NUMERIC","costUnitLabel":"hour"}]
                """);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void refusesAFixedCostWithNothingToPick() {
        // The one invariant the database cannot hold without a trigger.
        Owner ana = givenOwner("empty-choices");
        String listId = (String) newList(ana).get("id");

        ResponseEntity<String> response = putFields(ana, listId, versionOf(ana, listId),
                "[{\"kind\":\"COST\",\"label\":\"Time\",\"costMode\":\"FIXED\",\"choices\":[]}]");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(getFields(ana, listId)).hasSize(1);
    }

    @Test
    void refusesANumericCostThatIsNotMeasuredInAnything() {
        Owner ana = givenOwner("unitless");
        String listId = (String) newList(ana).get("id");

        ResponseEntity<String> response = putFields(ana, listId, versionOf(ana, listId),
                "[{\"kind\":\"COST\",\"label\":\"Money\",\"costMode\":\"NUMERIC\"}]");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void refusesTwoOfTheSameSingletonField() {
        Owner ana = givenOwner("double-assignee");
        String listId = (String) newList(ana).get("id");

        ResponseEntity<String> response = putFields(ana, listId, versionOf(ana, listId),
                "[{\"kind\":\"ASSIGNEE\",\"required\":true},{\"kind\":\"ASSIGNEE\"}]");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void renamingAChoiceKeepsItsIdRatherThanRecreatingIt() {
        // Item cost values will point at these ids. Delete-and-reinsert would
        // orphan every one of them, silently.
        Owner ana = givenOwner("renamer");
        String listId = (String) newList(ana).get("id");
        putFields(ana, listId, versionOf(ana, listId), """
                [{"kind":"COST","label":"Time","costMode":"FIXED","choices":[
                   {"label":"5 min","points":10},{"label":"30 min","points":50}]}]
                """);

        List<Map<String, Object>> before = getFields(ana, listId);
        Map<String, Object> cost = before.get(1);
        List<Map<String, Object>> choices = (List<Map<String, Object>>) cost.get("choices");
        String fieldId = (String) cost.get("id");
        String firstChoiceId = (String) choices.get(0).get("id");

        putFields(ana, listId, versionOf(ana, listId), """
                [{"id":"%s","kind":"COST","label":"Time","costMode":"FIXED","choices":[
                   {"id":"%s","label":"Five minutes","points":15},
                   {"id":"%s","label":"30 min","points":50}]}]
                """.formatted(fieldId, firstChoiceId, choices.get(1).get("id")));

        List<Map<String, Object>> after = getFields(ana, listId);
        Map<String, Object> renamed = after.get(1);
        assertThat(renamed.get("id")).isEqualTo(fieldId);
        List<Map<String, Object>> afterChoices = (List<Map<String, Object>>) renamed.get("choices");
        assertThat(afterChoices.get(0).get("id")).isEqualTo(firstChoiceId);
        assertThat(afterChoices.get(0)).containsEntry("label", "Five minutes")
                .containsEntry("points", 15);
    }

    @Test
    void reordersEveryFieldInOneTransaction() {
        // The deferred unique constraint on (list_id, position) is what makes
        // this possible. Without it the rewrite collides part-way through, on a
        // perfectly valid final state - and only a real database shows it.
        Owner ana = givenOwner("reorderer");
        String listId = (String) newList(ana).get("id");
        putFields(ana, listId, versionOf(ana, listId), """
                [{"kind":"DESCRIPTION","required":true},
                 {"kind":"ASSIGNEE","required":true},
                 {"kind":"DUE_AT"},
                 {"kind":"COST","label":"Time","costMode":"NUMERIC","costUnitLabel":"hour"}]
                """);
        List<Map<String, Object>> before = getFields(ana, listId);

        ResponseEntity<String> reordered = putFields(ana, listId, versionOf(ana, listId), """
                [{"id":"%s","kind":"DESCRIPTION","required":true},
                 {"id":"%s","kind":"COST","label":"Time","costMode":"NUMERIC","costUnitLabel":"hour"},
                 {"id":"%s","kind":"DUE_AT"},
                 {"id":"%s","kind":"ASSIGNEE","required":true}]
                """.formatted(before.get(0).get("id"), before.get(3).get("id"),
                        before.get(2).get("id"), before.get(1).get("id")));

        assertThat(reordered.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getFields(ana, listId)).extracting(field -> field.get("kind"))
                .containsExactly("DESCRIPTION", "COST", "DUE_AT", "ASSIGNEE");
    }

    @Test
    void reordersEveryChoiceInOneTransaction() {
        Owner ana = givenOwner("choice-reorderer");
        String listId = (String) newList(ana).get("id");
        putFields(ana, listId, versionOf(ana, listId), """
                [{"kind":"COST","label":"Time","costMode":"FIXED","choices":[
                   {"label":"a","points":1},{"label":"b","points":2},{"label":"c","points":3}]}]
                """);
        List<Map<String, Object>> cost = (List<Map<String, Object>>)
                getFields(ana, listId).get(1).get("choices");
        String fieldId = (String) getFields(ana, listId).get(1).get("id");

        ResponseEntity<String> response = putFields(ana, listId, versionOf(ana, listId), """
                [{"id":"%s","kind":"COST","label":"Time","costMode":"FIXED","choices":[
                   {"id":"%s","label":"c","points":3},
                   {"id":"%s","label":"b","points":2},
                   {"id":"%s","label":"a","points":1}]}]
                """.formatted(fieldId, cost.get(2).get("id"), cost.get(1).get("id"),
                        cost.get(0).get("id")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<Map<String, Object>>) getFields(ana, listId).get(1).get("choices"))
                .extracting(choice -> choice.get("label")).containsExactly("c", "b", "a");
    }

    @Test
    void droppingAFieldRemovesIt() {
        Owner ana = givenOwner("dropper");
        String listId = (String) newList(ana).get("id");
        putFields(ana, listId, versionOf(ana, listId),
                "[{\"kind\":\"DESCRIPTION\",\"required\":true},{\"kind\":\"DUE_AT\"}]");

        putFields(ana, listId, versionOf(ana, listId), "[{\"kind\":\"DESCRIPTION\",\"required\":true}]");

        assertThat(getFields(ana, listId)).hasSize(1);
    }

    @Test
    void switchingACostFromChoicesToANumberLeavesNoChoicesBehind() {
        Owner ana = givenOwner("switcher");
        String listId = (String) newList(ana).get("id");
        putFields(ana, listId, versionOf(ana, listId), """
                [{"kind":"COST","label":"Time","costMode":"FIXED","choices":[
                   {"label":"5 min","points":10}]}]
                """);
        String fieldId = (String) getFields(ana, listId).get(1).get("id");

        putFields(ana, listId, versionOf(ana, listId), """
                [{"id":"%s","kind":"COST","label":"Time","costMode":"NUMERIC",
                  "costUnitLabel":"hour","pointsPerUnit":1000}]
                """.formatted(fieldId));

        Map<String, Object> cost = getFields(ana, listId).get(1);
        assertThat(cost).containsEntry("costMode", "NUMERIC").containsEntry("costUnitLabel", "hour");
        assertThat((List<?>) cost.get("choices")).isEmpty();
    }

    @Test
    void changingTheConfigurationMovesTheListVersion() {
        Owner ana = givenOwner("versioner");
        String listId = (String) newList(ana).get("id");
        long before = versionOf(ana, listId);

        putFields(ana, listId, before, "[{\"kind\":\"DESCRIPTION\",\"required\":true}]");

        assertThat(versionOf(ana, listId)).isGreaterThan(before);
    }

    @Test
    void refusesAStaleConfigurationWrite() {
        Owner ana = givenOwner("stale-config");
        String listId = (String) newList(ana).get("id");
        long stale = versionOf(ana, listId);
        putFields(ana, listId, stale, "[{\"kind\":\"DUE_AT\"}]");

        ResponseEntity<String> second = putFields(ana, listId, stale, "[{\"kind\":\"ASSIGNEE\"}]");

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void aGranteeReadsTheConfigurationButCannotChangeIt() {
        Owner ana = givenOwner("config-owner");
        Owner ben = givenOwner("config-grantee");
        String listId = (String) newList(ana).get("id");
        rest.exchange("/api/lists/" + listId + "/access", HttpMethod.PUT,
                new HttpEntity<>("{\"version\":%d,\"memberIds\":[\"%s\"]}"
                        .formatted(versionOf(ana, listId), ben.id()), headersFor(ana.session())),
                List.class);

        assertThat(rest.exchange("/api/lists/" + listId + "/fields", HttpMethod.GET,
                new HttpEntity<>(headersFor(ben.session())), List.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        ResponseEntity<String> write = putFields(ben, listId, versionOf(ana, listId),
                "[{\"kind\":\"DUE_AT\"}]");
        assertThat(write.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void aStrangerCannotEvenSeeThatTheConfigurationExists() {
        Owner ana = givenOwner("config-private");
        Owner stranger = givenOwner("config-stranger");
        String listId = (String) newList(ana).get("id");

        assertThat(rest.exchange("/api/lists/" + listId + "/fields", HttpMethod.GET,
                new HttpEntity<>(headersFor(stranger.session())), String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
