package com.hatodo.calendar;

import com.hatodo.household.HouseholdProperties;
import com.hatodo.user.AppUser;
import com.hatodo.user.AppUserRepository;
import com.hatodo.user.CurrentUserProvider;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A member's calendar subscriptions, and who may see them
 * (requirements/0007 R62-R66).
 *
 * <p>Every authorization decision for a feed lives here. There is one
 * predicate and no second: <b>owned</b>. Reading a feed's events is a different
 * question, answered in SQL by {@link CalendarEventRepository}; everything on
 * this screen belongs to the caller.
 *
 * <p>Not the caller's answers <b>404</b>, not 403: a feed they were not granted
 * must not be discoverable by its id (adr/0010's rule, applied to a third kind
 * of thing).
 *
 * <p>There is deliberately no admin path. The admin administers accounts, not
 * contents, and "which calendars does Ben publish" is not account
 * administration.
 */
@Service
public class CalendarFeedService {

    /**
     * The preview makes this server fetch an address of the caller's choosing,
     * so it is throttled per caller as well as guarded. In-memory and
     * best-effort, like everything else that assumes one instance.
     */
    private static final Duration PREVIEW_INTERVAL = Duration.ofSeconds(3);

    /** Enough to recognise a calendar, few enough to read before confirming. */
    private static final int PREVIEW_EVENTS = 5;

    private final CalendarFeedRepository feeds;
    private final CalendarFeedAccessRepository access;
    private final CalendarEventRepository events;
    private final AppUserRepository users;
    private final CurrentUserProvider currentUser;
    private final FeedUrlGuard guard;
    private final FeedFetcher fetcher;
    private final IcsParser parser;
    private final CalendarWindow window;
    private final CalendarProperties properties;
    private final HouseholdProperties household;
    private final Clock clock;
    private final Map<UUID, Instant> lastPreview = new ConcurrentHashMap<>();

    public CalendarFeedService(CalendarFeedRepository feeds, CalendarFeedAccessRepository access,
                               CalendarEventRepository events, AppUserRepository users,
                               CurrentUserProvider currentUser, FeedUrlGuard guard,
                               FeedFetcher fetcher,
                               IcsParser parser, CalendarWindow window,
                               CalendarProperties properties, HouseholdProperties household,
                               Clock clock) {
        this.feeds = feeds;
        this.access = access;
        this.events = events;
        this.users = users;
        this.currentUser = currentUser;
        this.guard = guard;
        this.fetcher = fetcher;
        this.parser = parser;
        this.window = window;
        this.properties = properties;
        this.household = household;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<CalendarFeedResponse> mine() {
        UUID me = currentUser.requireUserId();
        return feeds.findOwnedBy(me).stream().map(this::describe).toList();
    }

    @Transactional(readOnly = true)
    public CalendarFeedResponse get(UUID feedId) {
        return describe(owned(feedId, currentUser.requireUserId()));
    }

    /**
     * Fetches an address and shows what came back, saving nothing (R65).
     *
     * <p>Handing a response body back to the member is only acceptable because
     * {@link FeedUrlGuard} decides where the body may come from.
     */
    @Transactional(readOnly = true)
    public CalendarPreviewResponse preview(String rawUrl) {
        UUID me = currentUser.requireUserId();
        // Guarded first, then throttled: the throttle exists to bound what this
        // server fetches, and an address the guard refuses is never fetched.
        // Counting a refusal would make a member who mistypes once wait before
        // they can correct it, for no safety at all.
        URI url = parseUrl(rawUrl);
        throttle(me);
        List<ParsedEvent> parsed = parser.parse(fetcher.fetch(url), household.zone(),
                window.from(), window.to());
        List<ParsedEvent> upcoming = parsed.stream()
                .filter(event -> !event.occursOn().isBefore(window.today()))
                .sorted(Comparator.comparing(ParsedEvent::occursOn)
                        .thenComparing(ParsedEvent::sortMinute))
                .toList();

        return new CalendarPreviewResponse(CalendarProvider.of(url), parsed.size(),
                upcoming.stream().limit(PREVIEW_EVENTS).map(CalendarEventResponse::of).toList());
    }

    /** @return the new feed's id; the caller refreshes it before reading it back */
    @Transactional
    public UUID create(SaveCalendarFeedRequest request) {
        AppUser me = requireCurrentUser();
        String label = request.label().trim();
        URI url = parseUrl(request.url());
        if (feeds.existsWithLabel(me.getId(), label, null)) {
            throw new CalendarFeedLabelTakenException(label);
        }
        if (feeds.existsWithUrl(me.getId(), CalendarFeed.digest(url.toString()), null)) {
            throw new DuplicateCalendarFeedException();
        }

        CalendarFeed feed = feeds.save(new CalendarFeed(me, label, url));
        // The owner's own grant, in the same transaction, so every later
        // membership question is one table - the convention data_list follows.
        access.save(new CalendarFeedAccess(feed, me));
        grantTo(feed, me, request.memberIds());
        return feed.getId();
    }

    /**
     * @return whether the address changed, which is the caller's cue to refresh
     */
    @Transactional
    public boolean update(UUID feedId, SaveCalendarFeedRequest request) {
        AppUser me = requireCurrentUser();
        CalendarFeed feed = owned(feedId, me.getId());

        String label = request.label().trim();
        if (feeds.existsWithLabel(me.getId(), label, feedId)) {
            throw new CalendarFeedLabelTakenException(label);
        }
        feed.setLabel(label);

        boolean addressChanged = false;
        // Null means "leave it alone", which is the only way to edit a feed:
        // the stored address is never readable back for the client to resend.
        if (request.url() != null && !request.url().isBlank()) {
            URI url = parseUrl(request.url());
            if (feeds.existsWithUrl(me.getId(), CalendarFeed.digest(url.toString()), feedId)) {
                throw new DuplicateCalendarFeedException();
            }
            addressChanged = !feed.getUrlDigest().equals(CalendarFeed.digest(url.toString()));
            feed.setUrl(url);
        }
        if (request.memberIds() != null) {
            replaceMembers(feed, me, request.memberIds());
        }
        return addressChanged;
    }

    @Transactional
    public void delete(UUID feedId) {
        UUID me = currentUser.requireUserId();
        // The grants and the events go with it, by cascade: nothing survives as
        // a snapshot of a calendar the household is no longer entitled to read
        // (R72).
        feeds.delete(owned(feedId, me));
    }

    /** The feeds the caller can see that have gone quiet (R71). */
    @Transactional(readOnly = true)
    public List<CalendarFeedWarningResponse> staleFor(UUID userId) {
        Instant cutoff = clock.instant().minus(properties.staleAfterOrSixHours());
        return feeds.findVisibleFor(userId).stream()
                .filter(feed -> isStale(feed, cutoff))
                .map(CalendarFeedWarningResponse::of)
                .toList();
    }

    private boolean isStale(CalendarFeed feed, Instant cutoff) {
        // A feed that has never succeeded is stale from the moment it is added,
        // which is correct: it is showing nothing and the member should know.
        return feed.getLastSuccessAt() == null || feed.getLastSuccessAt().isBefore(cutoff);
    }

    private CalendarFeed owned(UUID feedId, UUID me) {
        return feeds.findOwnedById(feedId, me)
                .orElseThrow(() -> new CalendarFeedNotFoundException(feedId));
    }

    private CalendarFeedResponse describe(CalendarFeed feed) {
        List<UUID> memberIds = access.findForFeed(feed.getId()).stream()
                .map(CalendarFeedAccess::getUserId)
                .filter(id -> !id.equals(feed.getOwnerId()))
                .toList();
        return new CalendarFeedResponse(feed.getId(), feed.getLabel(), feed.getProvider(),
                memberIds, feed.getLastSuccessAt(), feed.getLastError(),
                isStale(feed, clock.instant().minus(properties.staleAfterOrSixHours())),
                events.countForFeed(feed.getId()));
    }

    private void grantTo(CalendarFeed feed, AppUser owner, List<UUID> memberIds) {
        if (memberIds == null) {
            return;
        }
        Set<UUID> wanted = new LinkedHashSet<>(memberIds);
        // The owner is never a listed member: they always can see it, and a
        // checkbox that sometimes ticks itself is one nobody trusts.
        wanted.remove(owner.getId());
        for (UUID memberId : wanted) {
            AppUser member = users.findById(memberId)
                    .orElseThrow(UnknownCalendarMemberException::new);
            access.save(new CalendarFeedAccess(feed, member));
        }
    }

    private void replaceMembers(CalendarFeed feed, AppUser owner, List<UUID> memberIds) {
        Set<UUID> wanted = new LinkedHashSet<>(memberIds);
        wanted.remove(owner.getId());

        List<CalendarFeedAccess> existing = new ArrayList<>(access.findForFeed(feed.getId()));
        for (CalendarFeedAccess grant : existing) {
            if (!grant.getUserId().equals(owner.getId()) && !wanted.contains(grant.getUserId())) {
                // Revoked: the events leave that member's view on their next
                // read, because the grant join is the authorization (R72).
                access.delete(grant);
            }
        }
        Set<UUID> already = existing.stream().map(CalendarFeedAccess::getUserId)
                .collect(java.util.stream.Collectors.toSet());
        for (UUID memberId : wanted) {
            if (!already.contains(memberId)) {
                AppUser member = users.findById(memberId)
                        .orElseThrow(UnknownCalendarMemberException::new);
                access.save(new CalendarFeedAccess(feed, member));
            }
        }
    }

    private void throttle(UUID me) {
        Instant now = clock.instant();
        Instant previous = lastPreview.get(me);
        if (previous != null && previous.plus(PREVIEW_INTERVAL).isAfter(now)) {
            throw new PreviewTooOftenException();
        }
        lastPreview.put(me, now);
    }

    /**
     * Parsed and checked before anything is stored.
     *
     * <p>The fetcher checks again on every hop, which is where a redirect is
     * caught - but refusing at the door is what makes "add a calendar" fail
     * with a sentence the member can act on, rather than succeeding and leaving
     * an error on a row they then have to go and read.
     */
    private URI parseUrl(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new FeedUrlNotAllowedException("Paste the calendar's address.");
        }
        URI url;
        try {
            url = new URI(raw.trim());
        } catch (URISyntaxException malformed) {
            throw new FeedUrlNotAllowedException("That does not look like a web address.");
        }
        guard.check(url);
        return url;
    }

    private AppUser requireCurrentUser() {
        UUID id = currentUser.requireUserId();
        return users.findById(id).orElseThrow(
                () -> new IllegalStateException("Authenticated user " + id + " no longer exists"));
    }
}
