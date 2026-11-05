package com.hatodo.data;

import com.hatodo.list.ListIcon;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Entry search, in SQL (adr/0031).
 *
 * <p>Native and hand-written rather than a JPA query, for reasons that are all
 * about the tools: {@code tsvector}, {@code tsquery}, {@code @@},
 * {@code ts_rank}, {@code websearch_to_tsquery} and {@code similarity()} have
 * no JPQL equivalent, {@code tsvector} has no Hibernate basic type, the result
 * is a projection carrying the collection's name rather than an entity, and
 * nullable parameters need explicit casts that read far better in a string than
 * in an annotation. {@code StoredImageRepository.findForList} is the existing
 * precedent for dropping out of JPA when the entity graph is the wrong tool.
 *
 * <p>{@link NamedParameterJdbcTemplate} still joins the ambient transaction
 * through {@code DataSourceUtils}, so the service's boundary is unchanged. What
 * is given up is Hibernate flushing pending writes before the statement runs -
 * so search must never be called in the same transaction as an unflushed write
 * and expected to see it. Search is read-only, so that is a note for the future
 * rather than a live hazard.
 *
 * <p><b>One statement serves both routes.</b> Global and scoped differ by
 * whether {@code :listId} is null. Two implementations of this would diverge
 * within a month, and the one that gained a feature would not be the one that
 * failed a test.
 */
@Repository
public class DataEntrySearchQuery {

    /** A result set nobody asked to page through, bounded so it cannot be huge. */
    static final int DEFAULT_LIMIT = 50;
    static final int MAX_LIMIT = 200;

    /**
     * The weights {@code setweight} wrote into the document, in
     * {@code ts_rank}'s order, which is {D, C, B, A} - the reverse of how they
     * read in the migration. Getting this backwards silently inverts the
     * ranking, which is why the integration test asserts an order rather than a
     * set.
     */
    private static final String WEIGHTS = "'{0.1, 0.25, 0.5, 1.0}'::float4[]";

    /**
     * {@code rank/(rank+1)}, which keeps the score in [0,1) so a long detail
     * cannot outrank a short name by sheer volume.
     */
    private static final int NORMALISATION = 32;

    /**
     * The default: {@code plainto_tsquery} has already stemmed, dropped
     * stopwords and neutralised every operator character, so its text form is
     * nothing but quoted lexemes joined by {@code &} - which is what makes
     * promoting the last one to a prefix safe to do by rewriting text.
     * {@code nullif} turns an all-stopword query into NULL, which falls through
     * to the substring arm instead of erroring.
     */
    private static final String SIMPLE_TSQUERY = """
            to_tsquery('english',
                regexp_replace(
                    nullif(plainto_tsquery('english', CAST(:raw AS text))::text, ''),
                    '''$', ''':*'))
            """;

    /** Quotes or negation: the member asked for precision, so promote nothing. */
    private static final String ADVANCED_TSQUERY =
            "websearch_to_tsquery('english', CAST(:raw AS text))";

    private static final String BODY = """
            SELECT e.id, e.data_list_id, l.name AS list_name, l.icon AS list_icon,
                   e.name, e.description, e.tags, e.version, e.updated_at,
                   CASE WHEN q.tsq IS NOT NULL AND e.search_vector @@ q.tsq
                        THEN ts_rank(:::WEIGHTS:::, e.search_vector, q.tsq, :::NORM:::)
                        ELSE 0 END AS rank,
                   CASE WHEN CAST(:needle AS text) IS NULL THEN 0
                        ELSE similarity(lower(e.name), CAST(:needle AS text))
                        END AS name_similarity
            FROM data_entry e
            JOIN data_list l ON l.id = e.data_list_id
            CROSS JOIN q
            WHERE EXISTS (SELECT 1 FROM data_list_access a
                          WHERE a.data_list_id = l.id AND a.user_id = CAST(:userId AS uuid))
              AND (CAST(:listId AS uuid) IS NULL OR e.data_list_id = CAST(:listId AS uuid))
              AND (CAST(:tags AS text[]) IS NULL OR e.tags @> CAST(:tags AS text[]))
              AND (
                    -- "The member typed nothing", which is a fact about the
                    -- request and not about what parsed. Written as
                    -- "q.tsq IS NULL" it also caught a query that produced no
                    -- lexemes - a bare wildcard, a lone exclamation mark - and
                    -- matched every entry in every readable collection. The
                    -- member typed something, so the honest answer is no
                    -- matches rather than all of them.
                    (CAST(:raw AS text) IS NULL AND CAST(:needle AS text) IS NULL)
                 OR (q.tsq IS NOT NULL AND e.search_vector @@ q.tsq)
                 OR (CAST(:needle AS text) IS NOT NULL
                     AND (lower(e.name) LIKE CAST(:likePattern AS text) ESCAPE '\\'
                          OR EXISTS (SELECT 1 FROM unnest(e.tags) t
                                     WHERE t LIKE CAST(:likePattern AS text) ESCAPE '\\')))
              )
            ORDER BY rank DESC, name_similarity DESC, lower(e.name) ASC, e.id
            LIMIT :limit
            """
            // Not String.formatted: this body is full of SQL comments, and the
            // first one to contain a per-cent sign would make it an invalid
            // format string - which fails in a static initialiser, i.e. as an
            // unsatisfied bean dependency with a null cause. Ask how long that
            // took to find.
            .replace(":::WEIGHTS:::", WEIGHTS)
            .replace(":::NORM:::", String.valueOf(NORMALISATION));

    /**
     * Tags a collection already uses, commonest first (R52).
     *
     * <p>Grant-checked by the service before this runs, so the predicate here
     * is only the collection.
     */
    private static final String TAG_SUGGESTIONS = """
            SELECT t AS tag, COUNT(*) AS uses
            FROM data_entry e, unnest(e.tags) t
            WHERE e.data_list_id = CAST(:listId AS uuid)
              AND (CAST(:prefix AS text) IS NULL OR t LIKE CAST(:prefix AS text) ESCAPE '\\')
            GROUP BY t
            ORDER BY uses DESC, t ASC
            LIMIT 50
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public DataEntrySearchQuery(DataSource dataSource) {
        this.jdbc = new NamedParameterJdbcTemplate(dataSource);
    }

    /**
     * @param listId null for global search across every collection the caller
     *               can see (R54); non-null to scope to one
     * @param tags   AND semantics - narrowing, per R56
     */
    public List<DataEntryHit> search(UUID userId, UUID listId, DataSearchQueryText.Parsed parsed,
                                     List<String> tags, Integer limit) {
        // Every nullable parameter is cast in the SQL. Without the casts,
        // Postgres answers "could not determine data type of parameter $N" the
        // first time one is null - which is the global path, the common one.
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("listId", listId)
                .addValue("raw", parsed.raw())
                .addValue("needle", parsed.needle())
                .addValue("likePattern", parsed.likePattern())
                .addValue("tags", tags == null || tags.isEmpty() ? null : tags.toArray(new String[0]))
                .addValue("limit", boundedLimit(limit));

        String sql = "WITH q AS (SELECT " + tsqueryExpression(parsed) + " AS tsq) " + BODY;
        return jdbc.query(sql, params, HIT);
    }

    public List<TagSuggestionResponse> tagSuggestions(UUID listId, String prefix) {
        String pattern = prefix == null || prefix.isBlank()
                ? null
                : DataSearchQueryText.likePrefix(
                        prefix.trim().toLowerCase(java.util.Locale.ROOT));
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("listId", listId)
                .addValue("prefix", pattern);
        return jdbc.query(TAG_SUGGESTIONS, params,
                (rs, row) -> new TagSuggestionResponse(rs.getString("tag"), rs.getLong("uses")));
    }

    /**
     * Which tsquery function the statement calls. Chosen in Java, so the SQL
     * binds one parameter instead of evaluating two functions and relying on
     * {@code CASE} short-circuiting for safety - {@code to_tsquery} throws on
     * malformed input, and leaning on evaluation order for that is a bet.
     */
    private static String tsqueryExpression(DataSearchQueryText.Parsed parsed) {
        if (parsed.advanced()) {
            return ADVANCED_TSQUERY;
        }
        if (parsed.raw() == null) {
            return "NULL::tsquery";
        }
        return SIMPLE_TSQUERY;
    }

    private static int boundedLimit(Integer requested) {
        if (requested == null || requested <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(requested, MAX_LIMIT);
    }

    private static final RowMapper<DataEntryHit> HIT = (rs, row) -> new DataEntryHit(
            rs.getObject("id", UUID.class),
            rs.getObject("data_list_id", UUID.class),
            rs.getString("list_name"),
            ListIcon.valueOf(rs.getString("list_icon")),
            rs.getString("name"),
            rs.getString("description"),
            tagsOf(rs),
            rs.getLong("version"),
            rs.getTimestamp("updated_at").toInstant());

    private static List<String> tagsOf(ResultSet rs) throws SQLException {
        Array array = rs.getArray("tags");
        if (array == null) {
            return List.of();
        }
        return List.of((String[]) array.getArray());
    }
}
