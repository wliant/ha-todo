package com.hatodo.data;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The invariant the whole search design rests on: the columns the generated
 * {@code search_vector} reads cannot be written on their own (adr/0033).
 */
class DataEntryTest {

    @Test
    void settingDetailWritesBothColumns() {
        DataEntry entry = new DataEntry(null, "Tiramisu", null);

        entry.setDetail("<p>savoiardi</p>", "savoiardi");

        assertThat(entry.getDetailHtml()).isEqualTo("<p>savoiardi</p>");
        assertThat(entry.getDetailText()).isEqualTo("savoiardi");
    }

    @Test
    void settingTagsWritesBothColumns() {
        DataEntry entry = new DataEntry(null, "Tiramisu", null);

        entry.setTags(List.of("desserts", "italian"));

        assertThat(entry.getTags()).containsExactly("desserts", "italian");
        assertThat(entry.getTagsText()).isEqualTo("desserts italian");
    }

    @Test
    void noTagsMeansNoTagsTextRatherThanAnEmptyString() {
        DataEntry entry = new DataEntry(null, "Tiramisu", null);

        entry.setTags(List.of());

        assertThat(entry.getTags()).isEmpty();
        // One representation of "nothing to index", as the sanitizer does.
        assertThat(entry.getTagsText()).isNull();
    }

    @Test
    void theTagsGetterDoesNotExposeTheEntitysArray() {
        DataEntry entry = new DataEntry(null, "Tiramisu", null);
        entry.setTags(List.of("italian"));

        assertThat(entry.getTags()).isUnmodifiable();
    }

    /**
     * Cheap, and it is what makes the two columns' agreement structural rather
     * than a convention somebody has to remember. If this fails, somebody added
     * a setter and the index can now silently disagree with the content.
     */
    @Test
    void thereIsNoWayToWriteEitherIndexedColumnAlone() {
        List<String> setters = java.util.Arrays.stream(DataEntry.class.getMethods())
                .map(Method::getName)
                .filter(name -> name.startsWith("set"))
                .toList();

        assertThat(setters)
                .doesNotContain("setDetailHtml", "setDetailText", "setTagsText")
                .contains("setDetail", "setTags");
    }
}
