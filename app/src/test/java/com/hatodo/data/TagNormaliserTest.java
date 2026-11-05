package com.hatodo.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * The rules SQL cannot state. Pure, so these need neither a database nor a
 * Spring context - which is the point of the class being pure.
 */
class TagNormaliserTest {

    private final TagNormaliser normaliser = new TagNormaliser();

    @Test
    void lowercasesAndTrims() {
        assertThat(normaliser.normalise(List.of("  Quick ", "ITALIAN")))
                .containsExactly("italian", "quick");
    }

    @Test
    void deduplicates() {
        // Two chips reading "quick" is the thing this prevents.
        assertThat(normaliser.normalise(List.of("quick", "Quick", "QUICK")))
                .containsExactly("quick");
    }

    @Test
    void sortsSoAnEditThatChangesNothingDoesNotMoveTheVersion() {
        assertThat(normaliser.normalise(List.of("zebra", "apple")))
                .containsExactly("apple", "zebra");
        assertThat(normaliser.normalise(List.of("apple", "zebra")))
                .isEqualTo(normaliser.normalise(List.of("zebra", "apple")));
    }

    @Test
    void collapsesInternalWhitespace() {
        assertThat(normaliser.normalise(List.of("slow   cooker")))
                .containsExactly("slow cooker");
    }

    @Test
    void dropsBlankAndNullTagsRatherThanStoringThem() {
        // The database refuses a NULL element outright, which would be a 500
        // where a quiet drop is what the member meant.
        assertThat(normaliser.normalise(java.util.Arrays.asList("ok", "", "   ", null)))
                .containsExactly("ok");
    }

    @Test
    void rejectsAnOverLongTagWithASentence() {
        String tooLong = "x".repeat(TagNormaliser.MAX_LENGTH + 1);

        assertThatThrownBy(() -> normaliser.normalise(List.of(tooLong)))
                .isInstanceOf(InvalidTagException.class)
                .hasMessageContaining("too long");
    }

    @Test
    void acceptsATagOfExactlyTheMaximumLength() {
        String exact = "x".repeat(TagNormaliser.MAX_LENGTH);

        assertThat(normaliser.normalise(List.of(exact))).containsExactly(exact);
    }

    @Test
    void rejectsMoreThanTwentyTags() {
        List<String> many = IntStream.rangeClosed(0, TagNormaliser.MAX_TAGS)
                .mapToObj(i -> "tag" + i)
                .toList();

        assertThatThrownBy(() -> normaliser.normalise(many))
                .isInstanceOf(InvalidTagException.class);
    }

    @Test
    void countsTheLimitAfterDeduplicating() {
        // Twenty-one entries that are five distinct tags is five tags.
        List<String> repeated = IntStream.rangeClosed(0, TagNormaliser.MAX_TAGS)
                .mapToObj(i -> "tag" + (i % 5))
                .toList();

        assertThat(normaliser.normalise(repeated)).hasSize(5);
    }

    @Test
    void anEmptyListIsEmptyRatherThanNull() {
        assertThat(normaliser.normalise(List.of())).isEmpty();
    }
}
