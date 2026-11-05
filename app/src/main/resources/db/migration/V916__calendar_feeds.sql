-- Subscribed calendar feeds and the events read out of them
-- (requirements/0007 R62-R72).
--
-- A feed is somebody else's calendar, published by Google or Outlook at an
-- unguessable .ics URL. Nothing in this product creates, edits or deletes an
-- event: the feed is the truth and these rows are a copy of it, replaced
-- wholesale on every refresh. See docs/adr/0034 for why there is no OAuth here.
CREATE TABLE calendar_feed (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id    UUID         NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    label       VARCHAR(80)  NOT NULL,
    -- A bearer capability for the whole calendar behind it: whoever holds this
    -- string reads that calendar, indefinitely, with no session and no audit.
    -- Written and replaced, never selected into a response - CalendarFeedResponse
    -- has no component for it at all. Not encrypted, and requirements/0007
    -- records why: the only available key would sit in the same env file as
    -- DB_PASSWORD. See docs/adr/0035.
    url         TEXT         NOT NULL,
    -- sha256 of the URL, so one member cannot subscribe the same feed twice
    -- without an index over the secret itself.
    url_digest  VARCHAR(64)  NOT NULL,
    -- Inferred from the host, for display only. Nothing branches on it: one
    -- parser reads every provider, which is what adr/0034 buys.
    provider    VARCHAR(10)  NOT NULL,

    -- Sync state. R71 makes this part of the product rather than diagnostics:
    -- a feed that has quietly stopped working shows an empty Thursday, and the
    -- member believes it.
    last_attempt_at    TIMESTAMPTZ,
    last_success_at    TIMESTAMPTZ,
    last_error         VARCHAR(300),
    consecutive_errors SMALLINT     NOT NULL DEFAULT 0,

    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_calendar_feed_label_not_blank CHECK (btrim(label) <> '')
);

CREATE INDEX ix_calendar_feed_owner ON calendar_feed (owner_id);
CREATE UNIQUE INDEX ux_calendar_feed_owner_label ON calendar_feed (owner_id, lower(label));
CREATE UNIQUE INDEX ux_calendar_feed_owner_url ON calendar_feed (owner_id, url_digest);

-- Who may read this feed's events (R64). Binary, as every grant in this product
-- is, and the owner holds a row of their own - written in the same transaction,
-- exactly as on data_list - so every later question is one table.
CREATE TABLE calendar_feed_access (
    feed_id    UUID        NOT NULL REFERENCES calendar_feed (id) ON DELETE CASCADE,
    user_id    UUID        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    granted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (feed_id, user_id)
);

-- "Which calendars can I see" begins every read of the dashboard.
CREATE INDEX ix_calendar_feed_access_user ON calendar_feed_access (user_id);

-- One occurrence of one event.
--
-- A recurring series is stored already expanded, one row per occurrence inside
-- the cached window: the views ask "what is on this day", and a reader that had
-- to expand an RRULE to answer would be expanding it in three places.
CREATE TABLE calendar_event (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    feed_id       UUID         NOT NULL REFERENCES calendar_feed (id) ON DELETE CASCADE,
    -- RFC 5545 UID, kept because it is how the same appointment arriving
    -- through two members' feeds is recognised as one (R69).
    uid           TEXT         NOT NULL,
    summary       VARCHAR(300) NOT NULL,
    description   TEXT,
    location      VARCHAR(300),

    -- An all-day event is a date and a timed event is an instant, and the
    -- difference is structural rather than a flag somebody has to remember.
    -- Resolving a bare DATE to an instant has to choose a midnight to resolve
    -- it against, and the choice is wrong for every zone but one - which is how
    -- the school play ends up on the previous evening. See docs/adr/0036.
    all_day       BOOLEAN      NOT NULL,
    starts_at     TIMESTAMPTZ,
    ends_at       TIMESTAMPTZ,
    start_date    DATE,
    end_date      DATE,        -- exclusive, as DTEND is

    -- The household-zone day this occurrence is placed on, maintained by the
    -- parser. NOT a generated column: deriving it would need
    -- `starts_at AT TIME ZONE 'Asia/Singapore'`, and AT TIME ZONE is STABLE
    -- rather than IMMUTABLE - Postgres refuses it in a generation expression,
    -- and a zone baked into the schema could not be reconfigured anyway.
    occurs_on     DATE         NOT NULL,
    -- One orderable key for both kinds, so a merged day reads in order:
    -- -1 for an all-day event, so it sorts above every timed one on its day.
    sort_minute   SMALLINT     NOT NULL,

    CONSTRAINT ck_calendar_event_shape CHECK (
        (all_day AND start_date IS NOT NULL AND starts_at IS NULL)
        OR (NOT all_day AND starts_at IS NOT NULL AND start_date IS NULL)
    )
);

CREATE INDEX ix_calendar_event_feed ON calendar_event (feed_id);
CREATE INDEX ix_calendar_event_day ON calendar_event (occurs_on, sort_minute);

-- No version column on any of these. The poller is the only writer of
-- calendar_event and replaces a feed's rows wholesale; a feed is edited only by
-- its owner, from one screen, so there is no second editor to tell about.
