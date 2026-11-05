package com.hatodo.richtext;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class DetailImagesTest {

    private final DetailImages images = new DetailImages();

    private static final UUID ONE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TWO = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    void findsEveryOwnImageReference() {
        String html = "<p>a</p><img src=\"/api/images/%s\"><img src=\"/api/images/%s\">"
                .formatted(ONE, TWO);

        assertThat(images.referencedIn(html)).containsExactly(ONE, TWO);
    }

    @Test
    void readsEveryBodyItIsGiven() {
        assertThat(images.referencedIn(
                "<img src=\"/api/images/%s\">".formatted(ONE),
                "<img src=\"/api/images/%s\">".formatted(TWO)))
                .containsExactly(ONE, TWO);
    }

    @Test
    void ignoresNullBodies() {
        assertThat(images.referencedIn(null, null)).isEmpty();
    }

    @Test
    void matchesOurOwnPathWhereverItAppears() {
        assertThat(images.referencedIn(
                "<img src=\"https://elsewhere.test/api/images/%s\">".formatted(ONE)))
                .containsExactly(ONE);
        // Note what that asserts: the pattern matches the path wherever it
        // appears, which is safe only because the sanitizer has already
        // refused any src that is not exactly our own path. The claim check
        // then still requires the caller to have uploaded it.
    }

    @Test
    void deduplicates() {
        String twice = "<img src=\"/api/images/%s\"><img src=\"/api/images/%s\">"
                .formatted(ONE, ONE);

        assertThat(images.referencedIn(twice)).containsExactly(ONE);
    }

    @Test
    void ignoresThirtySixCharactersThatAreNotAUuid() {
        assertThat(images.referencedIn("<img src=\"/api/images/"
                + "zzzzzzzz-zzzz-zzzz-zzzz-zzzzzzzzzzzz\">")).isEmpty();
    }
}
