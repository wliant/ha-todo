package com.hatodo.data;

import org.springframework.stereotype.Component;

/**
 * Turns what a member typed into the parameters the search statement binds
 * (adr/0031).
 *
 * <p>Pure, and deliberately so: every decision here is one that would otherwise
 * be a {@code CASE} in SQL, testable only against a live database. Three
 * decisions, each with a trap behind it.
 */
@Component
public class DataSearchQueryText {

    /**
     * Below this a trigram index cannot help, and a one- or two-character
     * needle would match most of a collection.
     */
    static final int MIN_NEEDLE_LENGTH = 3;

    /**
     * @param raw         the member's trimmed text, bound as {@code :raw} and
     *                    handed to {@code plainto_tsquery} or
     *                    {@code websearch_to_tsquery} by the statement - null
     *                    when they typed nothing at all, which means "list
     *                    everything" rather than "match nothing"
     * @param advanced    true when they used quotes or negation, so the
     *                    statement calls {@code websearch_to_tsquery} and
     *                    promotes no prefix
     * @param needle      the lowercased single token for the substring arm, or
     *                    null to switch that arm off entirely
     * @param likePattern {@code %needle%} with LIKE's wildcards escaped, or
     *                    null alongside a null needle
     */
    public record Parsed(String raw, boolean advanced, String needle, String likePattern) {

        static final Parsed NOTHING = new Parsed(null, false, null, null);

        /** Nothing was typed: the caller wants the collection listed, not searched. */
        public boolean matchesEverything() {
            return raw == null;
        }
    }

    public Parsed parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Parsed.NOTHING;
        }
        String trimmed = raw.trim();

        if (isAdvanced(trimmed)) {
            // Quotes or a leading minus mean the member asked for precision.
            // websearch_to_tsquery handles both, and nothing is promoted to a
            // prefix: a prefix inside a phrase silently changes what the
            // phrase means. No substring arm either, for the same reason.
            return new Parsed(trimmed, true, null, null);
        }

        // The tsquery text is built by the statement from plainto_tsquery, not
        // here - plainto_tsquery is what neutralises every operator character,
        // and reimplementing that in Java is how an injection gets in. What
        // this decides is only *which* function the statement uses.
        String needle = needleFrom(trimmed);
        return new Parsed(trimmed, false, needle, needle == null ? null : like(needle));
    }

    /**
     * A quoted phrase or a negated term. Checked on the raw text because it is
     * a question about what the member typed, not about what survived parsing.
     */
    private static boolean isAdvanced(String raw) {
        if (raw.indexOf('"') >= 0) {
            return true;
        }
        for (String token : raw.split("\\s+")) {
            if (token.length() > 1 && token.charAt(0) == '-') {
                return true;
            }
        }
        return false;
    }

    /**
     * The substring arm runs for a single token only.
     *
     * <p>Multiple tokens mean the member is describing something, and full text
     * is better at that; a single short word is the case where they are
     * half-remembering a spelling, which is what R55 is for.
     */
    private static String needleFrom(String raw) {
        if (raw.contains(" ") || raw.length() < MIN_NEEDLE_LENGTH) {
            return null;
        }
        return raw.toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * Escapes LIKE's own wildcards, with {@code \} as the escape character -
     * which the statement declares with {@code ESCAPE '\'}.
     *
     * <p>Without this, a member searching for "100%" searches for "match
     * everything", and "_" is a silent single-character wildcard. The backslash
     * is doubled first, or escaping the others would be undone by it.
     */
    static String like(String needle) {
        return "%" + escape(needle) + "%";
    }

    /** The same escaping, anchored at the start - for tag suggestions as a member types. */
    static String likePrefix(String prefix) {
        return escape(prefix) + "%";
    }

    private static String escape(String value) {
        // The backslash is doubled first, or escaping the others would be
        // undone by it.
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
