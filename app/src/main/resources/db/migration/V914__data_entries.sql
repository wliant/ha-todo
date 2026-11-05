-- One entry in a collection (requirements/0006 R51, R52). Never assigned,
-- dated, completed or scored: a recipe is not work, and there is no column to
-- put any of that in - which is how 0006's exclusions are enforced.
CREATE TABLE data_entry (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    data_list_id UUID         NOT NULL REFERENCES data_list (id) ON DELETE CASCADE,

    name         VARCHAR(200) NOT NULL,
    description  VARCHAR(500),

    -- Sanitized server-side on every write under the same policy an item's
    -- detail uses (docs/adr/0018), with img src restricted to our own
    -- /api/images/{uuid} paths.
    detail_html  TEXT,

    -- The same rich text with the markup taken out, written by the application
    -- in the one setter that also writes detail_html, and never on its own.
    --
    -- Not a generated column. Postgres requires a generation expression to be
    -- IMMUTABLE, which leaves only a regexp - and the regexp that strips tags
    -- is wrong for a search document: replacing <[^>]*> with nothing turns
    -- "<li>milk</li><li>sugar</li>" into the single word "milksugar", and it
    -- leaves "caf&eacute;" indexed as "eacute". Tags must become spaces and
    -- entities must be decoded, which is a small parser rather than an
    -- expression. See docs/adr/0033.
    detail_text  TEXT,

    -- Free-form, lowercased, trimmed, de-duplicated and sorted by
    -- TagNormaliser. An array rather than a child table because the search
    -- document below is a *generated* column and a generated expression cannot
    -- see another table - tags in a child table are tags that cannot be in the
    -- vector. It also has no composite identity to collide, which is the
    -- NonUniqueObjectException trap item_cost_value already demonstrates.
    --
    -- This column is what R56's filter chips match, with @> and no stemming.
    tags         TEXT[]       NOT NULL DEFAULT '{}',

    -- The same tags joined by a space, written by the one setter that also
    -- writes tags. It exists because the *searchable* form of a tag has to be
    -- stemmed and positioned, and neither array_to_string(tags,' ') nor
    -- tags::text may appear in a generation expression - both go through an
    -- element output function and are only STABLE. See docs/adr/0033.
    tags_text    TEXT,

    version      BIGINT       NOT NULL DEFAULT 0,
    created_by   UUID         REFERENCES app_user (id) ON DELETE SET NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_data_entry_name_not_blank CHECK (btrim(name) <> ''),
    -- A NULL element would be an insert-time 500 where a 400 was the point.
    CONSTRAINT ck_data_entry_tags_no_nulls  CHECK (array_position(tags, NULL) IS NULL),
    CONSTRAINT ck_data_entry_tags_bounded   CHECK (cardinality(tags) <= 20)
);

-- The search document (requirements/0006 R53), weighted so a hit on the name
-- outranks a hit on a tag, which outranks the description, which outranks
-- something buried in the detail. That ordering is the difference between a
-- result list and a pile.
--
-- 'english' is passed explicitly because to_tsvector(regconfig, text) is
-- IMMUTABLE while the one-argument form reads default_text_search_config and is
-- only STABLE, which Postgres refuses here.
--
-- Every piece arrives as stemmed, positioned text. An earlier draft put the
-- tags in with setweight(array_to_tsvector(tags), 'B'); that creates, indexes
-- and queries without complaint and is silently inert, because
-- array_to_tsvector produces lexemes with no positions and a weight in
-- Postgres is a property of a position. It also made a tag unmatchable by a
-- member typing it, since the query side always stems. Measured and replaced in
-- docs/adr/0033.
ALTER TABLE data_entry ADD COLUMN search_vector tsvector
    GENERATED ALWAYS AS (
        setweight(to_tsvector('english', coalesce(name, '')),        'A')
     || setweight(to_tsvector('english', coalesce(tags_text, '')),   'B')
     || setweight(to_tsvector('english', coalesce(description, '')), 'C')
     || setweight(to_tsvector('english', coalesce(detail_text, '')), 'D')
    ) STORED;

CREATE INDEX ix_data_entry_search ON data_entry USING GIN (search_vector);

-- Filtering by tag (tags @> ARRAY[...]), and the suggestion endpoint's
-- GROUP BY over unnest.
CREATE INDEX ix_data_entry_tags ON data_entry USING GIN (tags);

-- The substring fallback (R55, docs/adr/0031). Only the name: a LIKE '%...%'
-- over every recipe body is a different feature with a different cost, and
-- nobody asked for it.
CREATE INDEX ix_data_entry_name_trgm ON data_entry USING GIN (lower(name) gin_trgm_ops);

-- Listing one collection in the order R57 displays it.
CREATE INDEX ix_data_entry_list ON data_entry (data_list_id, lower(name));
