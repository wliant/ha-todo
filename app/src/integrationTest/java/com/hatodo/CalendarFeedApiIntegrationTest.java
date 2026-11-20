package com.hatodo;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Calendar feeds against real Postgres and a real HTTP feed
 * (requirements/0007 R62-R72).
 *
 * <p>Two things here are the point. The address a member pastes is a bearer
 * capability for their whole calendar, so no response may carry it - and the
 * server fetches an address of the member's choosing, so the guard has to hold
 * through a redirect, which is only visible when something actually redirects.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
class CalendarFeedApiIntegrationTest extends ApiIntegrationTest {

    private static final ZoneId HOUSEHOLD = ZoneId.of("Asia/Singapore");

    private FixtureFeedServer feeds;

    @BeforeEach
    void startTheCalendarPublisher() {
        feeds = new FixtureFeedServer();
    }

    @AfterEach
    void stopTheCalendarPublisher() {
        feeds.close();
    }

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

    /** One timed event, today in the household's zone, so a window always finds it. */
    private String calendarWith(String summary) {
        LocalDate today = LocalDate.now(HOUSEHOLD);
        return """
                BEGIN:VCALENDAR
                VERSION:2.0
                PRODID:-//Fixture//EN
                BEGIN:VEVENT
                UID:%s
                SUMMARY:%s
                LOCATION:The hall
                DTSTART;TZID=Asia/Singapore:%sT193000
                DTEND;TZID=Asia/Singapore:%sT203000
                END:VEVENT
                END:VCALENDAR
                """.formatted(UUID.randomUUID(), summary,
                today.toString().replace("-", ""), today.toString().replace("-", ""));
    }

    private ResponseEntity<Map> subscribe(Member member, String label, String url,
                                          String... memberIds) {
        String members = memberIds.length == 0 ? "[]" : "[\"" + String.join("\",\"", memberIds) + "\"]";
        return rest.exchange("/api/me/calendars", HttpMethod.POST,
                new HttpEntity<>("{\"label\":\"%s\",\"url\":\"%s\",\"memberIds\":%s}"
                        .formatted(label, url, members), headersFor(member.session())), Map.class);
    }

    private List<Map<String, Object>> myFeeds(Member member) {
        ResponseEntity<List> response = rest.exchange("/api/me/calendars", HttpMethod.GET,
                new HttpEntity<>(headersFor(member.session())), List.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private Map<String, Object> scheduledToday(Member member) {
        Instant from = Instant.now().minus(2, ChronoUnit.DAYS);
        Instant to = Instant.now().plus(2, ChronoUnit.DAYS);
        ResponseEntity<Map> response = rest.exchange(
                "/api/dashboard/scheduled?from=%s&to=%s".formatted(from, to),
                HttpMethod.GET, new HttpEntity<>(headersFor(member.session())), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private List<Map<String, Object>> eventsFor(Member member) {
        return (List<Map<String, Object>>) scheduledToday(member).get("events");
    }

    @Test
    void subscribingACalendarFetchesItAndPutsItsEventsOnTheDashboard() {
        Member ana = givenMember("cal-ana");
        String url = feeds.serving("/ana.ics", calendarWith("Parents evening"));

        ResponseEntity<Map> created = subscribe(ana, "Family", url);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(created.getBody().get("provider")).isEqualTo("OTHER");
        assertThat(created.getBody().get("lastError")).isNull();
        assertThat(((Number) created.getBody().get("eventCount")).intValue()).isEqualTo(1);
        assertThat(eventsFor(ana)).extracting(event -> event.get("summary"))
                .contains("Parents evening");
    }

    @Test
    void neverReturnsTheAddressToAnybodyIncludingItsOwner() {
        Member ana = givenMember("cal-secret");
        String url = feeds.serving("/secret.ics", calendarWith("Private thing"));

        ResponseEntity<Map> created = subscribe(ana, "Family", url);
        ResponseEntity<String> listed = rest.exchange("/api/me/calendars", HttpMethod.GET,
                new HttpEntity<>(headersFor(ana.session())), String.class);

        // Not "no url field": the address must not appear anywhere in the JSON.
        assertThat(created.getBody()).doesNotContainKey("url");
        assertThat(listed.getBody()).doesNotContain(url).doesNotContain("secret.ics");
    }

    @Test
    void showsEventsToAMemberGrantedAccessAndNotToAnybodyElse() {
        Member ana = givenMember("cal-owner");
        Member ben = givenMember("cal-granted");
        Member stranger = givenMember("cal-stranger");
        String url = feeds.serving("/shared.ics", calendarWith("Shared dinner"));

        subscribe(ana, "Family", url, ben.id().toString());

        assertThat(eventsFor(ben)).extracting(event -> event.get("summary"))
                .contains("Shared dinner");
        assertThat(eventsFor(stranger)).extracting(event -> event.get("summary"))
                .doesNotContain("Shared dinner");
    }

    @Test
    void takesTheEventsBackTheMomentAGrantIsRevoked() {
        Member ana = givenMember("cal-revoke-owner");
        Member ben = givenMember("cal-revoke-member");
        String url = feeds.serving("/revoked.ics", calendarWith("Was shared"));
        String feedId = (String) subscribe(ana, "Family", url, ben.id().toString())
                .getBody().get("id");

        assertThat(eventsFor(ben)).extracting(event -> event.get("summary")).contains("Was shared");

        rest.exchange("/api/me/calendars/" + feedId, HttpMethod.PATCH,
                new HttpEntity<>("{\"label\":\"Family\",\"memberIds\":[]}",
                        headersFor(ana.session())), Map.class);

        // Nothing survives as a snapshot of a calendar he is no longer
        // entitled to read (R72) - and nothing had to be cleaned up, because
        // the grant join is the authorization.
        assertThat(eventsFor(ben)).extracting(event -> event.get("summary"))
                .doesNotContain("Was shared");
        assertThat(eventsFor(ana)).extracting(event -> event.get("summary")).contains("Was shared");
    }

    @Test
    void deletingAFeedTakesItsEventsWithIt() {
        Member ana = givenMember("cal-delete");
        String url = feeds.serving("/doomed.ics", calendarWith("Going away"));
        String feedId = (String) subscribe(ana, "Family", url).getBody().get("id");

        ResponseEntity<Void> deleted = rest.exchange("/api/me/calendars/" + feedId,
                HttpMethod.DELETE, new HttpEntity<>(headersFor(ana.session())), Void.class);

        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(eventsFor(ana)).extracting(event -> event.get("summary"))
                .doesNotContain("Going away");
        assertThat(myFeeds(ana)).isEmpty();
    }

    @Test
    void refusesToTouchAFeedTheCallerDoesNotOwnWithoutAdmittingItExists() {
        Member ana = givenMember("cal-mine");
        Member ben = givenMember("cal-notmine");
        String url = feeds.serving("/anas.ics", calendarWith("Ana's thing"));
        String feedId = (String) subscribe(ana, "Family", url, ben.id().toString())
                .getBody().get("id");

        // Ben can see the events; he still cannot touch the feed, and an id is
        // not proof of anything.
        for (HttpMethod method : List.of(HttpMethod.PATCH, HttpMethod.DELETE)) {
            ResponseEntity<Map> response = rest.exchange("/api/me/calendars/" + feedId, method,
                    new HttpEntity<>("{\"label\":\"Stolen\"}", headersFor(ben.session())), Map.class);
            assertThat(response.getStatusCode()).as("%s by a non-owner", method)
                    .isEqualTo(HttpStatus.NOT_FOUND);
        }
        ResponseEntity<Map> refreshed = rest.exchange("/api/me/calendars/" + feedId + "/refresh",
                HttpMethod.POST, new HttpEntity<>("", headersFor(ben.session())), Map.class);
        assertThat(refreshed.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(myFeeds(ben)).isEmpty();
    }

    @Test
    void refusesAnAddressOnThisMachineTheWayADeploymentWould() {
        // The itest profile allows private hosts so the fixture server can be
        // reached at all, so what is asserted here is the shape of the refusal
        // the *deployment* gives: FeedUrlGuardTest owns the decision table.
        Member ana = givenMember("cal-ssrf");

        ResponseEntity<Map> refused = subscribe(ana, "Registry", "ftp://192.168.9.9:5000/v2/");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(myFeeds(ana)).isEmpty();
    }

    @Test
    void keepsAFeedThatCannotBeReadAndSaysSoRatherThanFailingTheRequest() {
        Member ana = givenMember("cal-404");
        String url = feeds.answering("/gone.ics", 404, "not here");

        ResponseEntity<Map> created = subscribe(ana, "Regenerated", url);

        // Subscribed, visibly broken: the member fixes the address rather than
        // wondering why nothing happened. R71's whole point.
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((String) created.getBody().get("lastError")).contains("not found");
        assertThat(created.getBody().get("stale")).isEqualTo(true);
        assertThat(scheduledToday(ana).get("staleFeeds").toString()).contains("Regenerated");
    }

    @Test
    void keepsTheEventsItAlreadyHasWhenARefreshFails() {
        Member ana = givenMember("cal-flap");
        String url = feeds.serving("/flaky.ics", calendarWith("Still here"));
        String feedId = (String) subscribe(ana, "Flaky", url).getBody().get("id");

        // The provider starts failing, and the member asks for a refresh.
        feeds.answering("/flaky.ics", 500, "sorry");
        rest.exchange("/api/me/calendars/" + feedId + "/refresh", HttpMethod.POST,
                new HttpEntity<>("", headersFor(ana.session())), Map.class);

        // A hiccup must not empty Thursday.
        assertThat(eventsFor(ana)).extracting(event -> event.get("summary")).contains("Still here");
    }

    @Test
    void refusesACalendarThatRedirectsToThisMachineInADeploymentShape() {
        // Private hosts are allowed here, so this asserts the redirect is
        // followed and re-checked at all - the hop is where a public host would
        // otherwise smuggle in 127.0.0.1.
        Member ana = givenMember("cal-redirect");
        String target = feeds.serving("/final.ics", calendarWith("After the hop"));
        String url = feeds.redirectingTo("/hop.ics", target);

        ResponseEntity<Map> created = subscribe(ana, "Redirected", url);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(eventsFor(ana)).extracting(event -> event.get("summary"))
                .contains("After the hop");
    }

    @Test
    void givesUpOnACalendarTooLargeToRead() {
        Member ana = givenMember("cal-huge");
        String url = feeds.servingTooMuch("/huge.ics");

        ResponseEntity<Map> created = subscribe(ana, "Enormous", url);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((String) created.getBody().get("lastError")).isNotNull();
        assertThat(((Number) created.getBody().get("eventCount")).intValue()).isZero();
    }

    @Test
    void showsOneAppointmentOnceWhenTwoMembersFeedsBothCarryIt() {
        Member ana = givenMember("cal-dedup-ana");
        Member ben = givenMember("cal-dedup-ben");
        LocalDate today = LocalDate.now(HOUSEHOLD);
        String shared = """
                BEGIN:VCALENDAR
                VERSION:2.0
                BEGIN:VEVENT
                UID:one-dinner-for-two-feeds
                SUMMARY:Dinner together
                DTSTART;TZID=Asia/Singapore:%sT190000
                DTEND;TZID=Asia/Singapore:%sT210000
                END:VEVENT
                END:VCALENDAR
                """.formatted(today.toString().replace("-", ""),
                today.toString().replace("-", ""));

        subscribe(ana, "Ana's", feeds.serving("/ana-copy.ics", shared), ben.id().toString());
        subscribe(ben, "Ben's", feeds.serving("/ben-copy.ics", shared), ana.id().toString());

        List<Map<String, Object>> anasView = eventsFor(ana).stream()
                .filter(event -> "Dinner together".equals(event.get("summary")))
                .toList();

        assertThat(anasView).as("one appointment, not two rows").hasSize(1);
        assertThat((List<Map<String, Object>>) anasView.get(0).get("sources")).hasSize(2);
    }

    @Test
    void previewShowsWhatTheHouseholdWouldSeeWithoutSubscribingAnything() {
        Member ana = givenMember("cal-preview");
        String url = feeds.serving("/preview.ics", calendarWith("About to be shared"));

        ResponseEntity<Map> preview = rest.exchange("/api/me/calendars/preview", HttpMethod.POST,
                new HttpEntity<>("{\"url\":\"%s\"}".formatted(url), headersFor(ana.session())),
                Map.class);

        assertThat(preview.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(preview.getBody().toString()).contains("About to be shared");
        assertThat(myFeeds(ana)).as("nothing is saved until the member confirms").isEmpty();
    }

    @Test
    void refusesTheSameCalendarTwiceAndTheSameNameTwice() {
        Member ana = givenMember("cal-dupes");
        String url = feeds.serving("/once.ics", calendarWith("Once"));
        String other = feeds.serving("/twice.ics", calendarWith("Twice"));
        subscribe(ana, "Family", url);

        assertThat(subscribe(ana, "Family again", url).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(subscribe(ana, "Family", other).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void anAllDayEventLandsOnItsOwnDateAndCarriesNoInstant() {
        Member ana = givenMember("cal-allday");
        LocalDate today = LocalDate.now(HOUSEHOLD);
        String ics = """
                BEGIN:VCALENDAR
                VERSION:2.0
                BEGIN:VEVENT
                UID:school-play
                SUMMARY:School play
                DTSTART;VALUE=DATE:%s
                DTEND;VALUE=DATE:%s
                END:VEVENT
                END:VCALENDAR
                """.formatted(today.toString().replace("-", ""),
                today.plusDays(1).toString().replace("-", ""));

        subscribe(ana, "School", feeds.serving("/school.ics", ics));

        assertThat(eventsFor(ana)).filteredOn(event -> "School play".equals(event.get("summary")))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.get("allDay")).isEqualTo(true);
                    assertThat(event.get("startDate")).isEqualTo(today.toString());
                    // The whole of adr/0036: no midnight was chosen for it.
                    assertThat(event.get("startsAt")).isNull();
                    assertThat(((Number) event.get("sortMinute")).intValue()).isEqualTo(-1);
                });
    }
}
