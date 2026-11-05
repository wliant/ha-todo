package com.hatodo.data;

import static org.assertj.core.api.Assertions.assertThat;

import com.hatodo.data.DataSearchQueryText.Parsed;
import org.junit.jupiter.api.Test;

/**
 * Every decision adr/0031 moved out of SQL so it could be tested without a
 * database. The SQL that consumes these is asserted in
 * DataEntrySearchApiIntegrationTest, against real Postgres.
 */
class DataSearchQueryTextTest {

    private final DataSearchQueryText parser = new DataSearchQueryText();

    @Test
    void blankQueryMatchesEverythingRatherThanNothing() {
        // A collection with an empty search box is listed, not emptied.
        assertThat(parser.parse(null).matchesEverything()).isTrue();
        assertThat(parser.parse("   ").matchesEverything()).isTrue();
        assertThat(parser.parse("").needle()).isNull();
    }

    @Test
    void aPlainWordIsSimpleAndCarriesASubstringNeedle() {
        Parsed parsed = parser.parse("avian");

        assertThat(parsed.advanced()).isFalse();
        assertThat(parsed.raw()).isEqualTo("avian");
        assertThat(parsed.needle()).isEqualTo("avian");
        assertThat(parsed.likePattern()).isEqualTo("%avian%");
    }

    @Test
    void choosesWebsearchForAQuotedPhrase() {
        Parsed parsed = parser.parse("\"slow cooker\"");

        assertThat(parsed.advanced()).isTrue();
        // No prefix promotion, and no substring arm: a member who typed quotes
        // asked for precision, and a prefix inside a phrase changes what the
        // phrase means.
        assertThat(parsed.needle()).isNull();
    }

    @Test
    void choosesWebsearchForANegatedTerm() {
        Parsed parsed = parser.parse("pasta -cream");

        assertThat(parsed.advanced()).isTrue();
        assertThat(parsed.needle()).isNull();
    }

    @Test
    void aLoneHyphenIsNotNegation() {
        // "-" on its own is somebody typing, not an operator.
        assertThat(parser.parse("-").advanced()).isFalse();
    }

    @Test
    void noNeedleForAMultiTokenQuery() {
        // Several words means the member is describing something, which full
        // text is better at than a substring scan.
        assertThat(parser.parse("coffee mascarpone").needle()).isNull();
    }

    @Test
    void noNeedleForATwoCharacterQuery() {
        // A trigram index cannot help below three characters, and one letter
        // would match most of a collection.
        assertThat(parser.parse("av").needle()).isNull();
        assertThat(parser.parse("avi").needle()).isEqualTo("avi");
    }

    @Test
    void escapesLikeWildcards() {
        // Without this, searching for "100%" searches for "match everything".
        assertThat(parser.parse("100%").likePattern()).isEqualTo("%100\\%%");
        assertThat(parser.parse("a_b").likePattern()).isEqualTo("%a\\_b%");
    }

    @Test
    void escapesTheEscapeCharacterFirst() {
        // Doubling the backslash last would undo the other two escapes.
        assertThat(DataSearchQueryText.like("a\\%b")).isEqualTo("%a\\\\\\%b%");
    }

    @Test
    void aPrefixPatternIsAnchoredAtTheStart() {
        assertThat(DataSearchQueryText.likePrefix("ital")).isEqualTo("ital%");
        assertThat(DataSearchQueryText.likePrefix("50%")).isEqualTo("50\\%%");
    }

    @Test
    void lowercasesTheNeedleBecauseTheIndexIsOnLowerName() {
        assertThat(parser.parse("Avian").needle()).isEqualTo("avian");
    }
}
