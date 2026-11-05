package com.hatodo.richtext;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * The index is exactly as good as this class is correct, so the cases are the
 * specific ways a naive implementation gets it wrong rather than a general
 * "does it strip tags".
 */
class HtmlTextTest {

    private final HtmlText text = new HtmlText();

    @Test
    void replacesBlockBoundariesWithSpaceSoWordsDoNotMerge() {
        // The whole reason this class exists rather than a regexp inside a
        // generated column: replacing tags with the empty string indexes
        // "milksugar", one lexeme nobody will ever type (adr/0033).
        assertThat(text.toPlainText("<ul><li>milk</li><li>sugar</li></ul>"))
                .isEqualTo("milk sugar");
    }

    @Test
    void doesNotMergeAcrossParagraphsEither() {
        assertThat(text.toPlainText("<p>first</p><p>second</p>")).isEqualTo("first second");
    }

    @Test
    void decodesNamedEntities() {
        // "caf&eacute;" is not in our sanitizer's output, but "&amp;" and
        // "&nbsp;" are, and an undecoded one contributes a lexeme the member
        // would never search for.
        assertThat(text.toPlainText("<p>salt&nbsp;&amp;&nbsp;pepper</p>"))
                .isEqualTo("salt & pepper");
    }

    @Test
    void decodesNumericEntities() {
        assertThat(text.toPlainText("<p>caf&#233;</p>")).isEqualTo("café");
        assertThat(text.toPlainText("<p>caf&#xe9;</p>")).isEqualTo("café");
    }

    @Test
    void survivesAnAbsurdNumericEntityRatherThanFailingAWrite() {
        assertThat(text.toPlainText("<p>a&#999999999999;b</p>")).isEqualTo("a b");
    }

    @Test
    void keepsLinkTextAndDropsTheMarkup() {
        assertThat(text.toPlainText("<p>see <a href=\"https://x.test\">the recipe</a></p>"))
                .isEqualTo("see the recipe");
    }

    @Test
    void dropsAnImageEntirelyBecauseThereIsNoTextInIt() {
        assertThat(text.toPlainText("<img src=\"/api/images/x\" alt=\"\">")).isNull();
    }

    @Test
    void collapsesWhitespace() {
        assertThat(text.toPlainText("<p>a   \n  b</p>")).isEqualTo("a b");
    }

    @Test
    void returnsNullWhenNothingIsLeft() {
        assertThat(text.toPlainText(null)).isNull();
        assertThat(text.toPlainText("")).isNull();
        assertThat(text.toPlainText("   ")).isNull();
        assertThat(text.toPlainText("<p></p>")).isNull();
    }
}
