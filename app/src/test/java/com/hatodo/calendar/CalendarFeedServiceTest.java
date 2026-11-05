package com.hatodo.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hatodo.household.HouseholdProperties;
import com.hatodo.user.AppUser;
import com.hatodo.user.AppUserRepository;
import com.hatodo.user.CurrentUserProvider;
import java.lang.reflect.RecordComponent;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CalendarFeedServiceTest {

    private static final UUID ANA = UUID.fromString("00000000-0000-0000-0000-00000000a111");
    private static final UUID BEN = UUID.fromString("00000000-0000-0000-0000-00000000b222");
    private static final URI FEED = URI.create("https://calendar.google.com/calendar/ical/x/basic.ics");

    @Mock
    private CalendarFeedRepository feeds;
    @Mock
    private CalendarFeedAccessRepository access;
    @Mock
    private CalendarEventRepository events;
    @Mock
    private AppUserRepository users;
    @Mock
    private CurrentUserProvider currentUser;
    @Mock
    private FeedFetcher fetcher;
    @Mock
    private CalendarWindow window;

    private AppUser ana;
    private CalendarFeedService service;

    @BeforeEach
    void setUp() {
        ana = AppUser.passwordlessMember("ana", "Ana");
        ReflectionTestUtils.setField(ana, "id", ANA);
        when(currentUser.requireUserId()).thenReturn(ANA);
        when(users.findById(ANA)).thenReturn(Optional.of(ana));
        when(window.today()).thenReturn(java.time.LocalDate.of(2026, 11, 6));
        when(window.from()).thenReturn(java.time.LocalDate.of(2026, 9, 22));
        when(window.to()).thenReturn(java.time.LocalDate.of(2027, 2, 4));
        when(feeds.save(any())).thenAnswer(call -> {
            CalendarFeed feed = call.getArgument(0);
            if (feed.getId() == null) {
                ReflectionTestUtils.setField(feed, "id", UUID.randomUUID());
            }
            return feed;
        });

        // Private hosts allowed, so these tests need no DNS and no network.
        service = serviceWith(true);
    }

    private CalendarFeedService serviceWith(boolean allowPrivateHosts) {
        CalendarProperties properties =
                new CalendarProperties(true, null, null, null, null, allowPrivateHosts);
        return new CalendarFeedService(feeds, access, events, users, currentUser,
                new FeedUrlGuard(properties), fetcher, new IcsParser(), window, properties,
                new HouseholdProperties("Asia/Singapore"),
                Clock.fixed(Instant.parse("2026-11-06T02:00:00Z"), ZoneId.of("UTC")));
    }

    @Test
    void theFeedResponseHasNowhereToPutTheAddress() {
        // The guarantee adr/0035 rests on: not "we remember not to populate
        // it", but "there is no field". If somebody adds one, this fails before
        // a calendar leaks.
        assertThat(CalendarFeedResponse.class.getRecordComponents())
                .extracting(RecordComponent::getName)
                .doesNotContain("url", "address", "feedUrl", "secret");
    }

    @Test
    void subscribingWritesTheOwnersOwnGrantSoEveryLaterQuestionIsOneTable() {
        service.create(new SaveCalendarFeedRequest("Family", FEED.toString(), List.of()));

        verify(access).save(any(CalendarFeedAccess.class));
    }

    @Test
    void aNewFeedIsVisibleToNobodyButItsOwner() {
        service.create(new SaveCalendarFeedRequest("Family", FEED.toString(), List.of()));

        // One save: the owner's. Nobody is granted by default, because a feed
        // publishes somebody's whole calendar (R64).
        verify(access, never()).save(argThatGrants(BEN));
    }

    @Test
    void refusesAFeedTheCallerDoesNotOwnWithoutSayingItExists() {
        UUID bensFeed = UUID.randomUUID();
        when(feeds.findOwnedById(eq(bensFeed), eq(ANA))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(bensFeed))
                .isInstanceOf(CalendarFeedNotFoundException.class);
    }

    @Test
    void refusesASecondFeedWithTheSameLabel() {
        when(feeds.existsWithLabel(eq(ANA), eq("Family"), eq(null))).thenReturn(true);

        assertThatThrownBy(() -> service.create(
                new SaveCalendarFeedRequest("Family", FEED.toString(), List.of())))
                .isInstanceOf(CalendarFeedLabelTakenException.class);
    }

    @Test
    void refusesTheSameAddressTwiceForOneMember() {
        when(feeds.existsWithUrl(eq(ANA), any(), eq(null))).thenReturn(true);

        assertThatThrownBy(() -> service.create(
                new SaveCalendarFeedRequest("Another name", FEED.toString(), List.of())))
                .isInstanceOf(DuplicateCalendarFeedException.class);
    }

    @Test
    void refusesAnAddressThisServerMustNotFetch() {
        // Configured as a deployment is, which is the only configuration where
        // this matters: the registry on 192.168.1.248:5000 has no
        // authentication and the app container can reach it (adr/0035).
        CalendarFeedService deployed = serviceWith(false);

        assertThatThrownBy(() -> deployed.create(new SaveCalendarFeedRequest(
                "Registry", "http://192.168.1.248:5000/v2/_catalog", List.of())))
                .isInstanceOf(FeedUrlNotAllowedException.class);
        verify(feeds, never()).save(any());
    }

    @Test
    void neverGrantsTheOwnerAsIfTheyWereAMember() {
        service.create(new SaveCalendarFeedRequest("Family", FEED.toString(), List.of(ANA)));

        // One save, the owner's. Asked to grant themselves they get nothing
        // extra: a checkbox that sometimes ticks itself is one nobody trusts.
        verify(access, org.mockito.Mockito.times(1)).save(any(CalendarFeedAccess.class));
    }

    @Test
    void callsAMemberWhoNoLongerExistsOut() {
        when(users.findById(BEN)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(
                new SaveCalendarFeedRequest("Family", FEED.toString(), List.of(BEN))))
                .isInstanceOf(UnknownCalendarMemberException.class);
    }

    @Test
    void previewingTwiceInARowIsRefusedRatherThanAmplified() {
        when(fetcher.fetch(any())).thenReturn("""
                BEGIN:VCALENDAR
                VERSION:2.0
                END:VCALENDAR
                """);

        service.preview(FEED.toString());

        assertThatThrownBy(() -> service.preview(FEED.toString()))
                .isInstanceOf(PreviewTooOftenException.class);
    }

    @Test
    void aRefusedAddressDoesNotCostTheMemberTheirNextAttempt() {
        // The throttle bounds outbound fetches, and a refusal makes none - so a
        // member who mistypes once must not have to wait to correct it.
        when(fetcher.fetch(any())).thenReturn("BEGIN:VCALENDAR\nVERSION:2.0\nEND:VCALENDAR\n");
        CalendarFeedService deployed = serviceWith(false);

        assertThatThrownBy(() -> deployed.preview("http://10.0.0.1/basic.ics"))
                .isInstanceOf(FeedUrlNotAllowedException.class);

        assertThatCode(() -> service.preview(FEED.toString())).doesNotThrowAnyException();
    }

    @Test
    void previewShowsWhatTheHouseholdWouldSeeAndStoresNothing() {
        when(fetcher.fetch(any())).thenReturn("""
                BEGIN:VCALENDAR
                VERSION:2.0
                BEGIN:VEVENT
                UID:a
                SUMMARY:Parents evening
                DTSTART;TZID=Asia/Singapore:20261107T193000
                DTEND;TZID=Asia/Singapore:20261107T203000
                END:VEVENT
                END:VCALENDAR
                """);

        CalendarPreviewResponse preview = service.preview(FEED.toString());

        assertThat(preview.provider()).isEqualTo(CalendarProvider.GOOGLE);
        assertThat(preview.totalInWindow()).isEqualTo(1);
        assertThat(preview.next()).singleElement()
                .satisfies(event -> assertThat(event.summary()).isEqualTo("Parents evening"));
        verify(feeds, never()).save(any());
    }

    private static CalendarFeedAccess argThatGrants(UUID userId) {
        return org.mockito.ArgumentMatchers.argThat(
                grant -> grant != null && userId.equals(grant.getUserId()));
    }
}
