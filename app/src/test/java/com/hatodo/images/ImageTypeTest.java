package com.hatodo.images;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * What the API will later serve from our own origin is decided here, so it is
 * decided by the bytes - never by a filename or a declared Content-Type, both
 * of which belong to whoever is uploading.
 */
class ImageTypeTest {

    private static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = (byte) values[i];
        }
        return result;
    }

    @Test
    void recognisesAPng() {
        assertThat(ImageType.sniff(bytes(0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0)))
                .isEqualTo(ImageType.PNG);
    }

    @Test
    void recognisesAJpeg() {
        assertThat(ImageType.sniff(bytes(0xFF, 0xD8, 0xFF, 0xE0, 0, 0))).isEqualTo(ImageType.JPEG);
    }

    @Test
    void recognisesAWebp() {
        assertThat(ImageType.sniff(bytes('R', 'I', 'F', 'F', 1, 2, 3, 4, 'W', 'E', 'B', 'P')))
                .isEqualTo(ImageType.WEBP);
    }

    @Test
    void refusesARiffContainerThatIsNotWebp() {
        // A WAV file starts RIFF too, and is not something to serve as a face.
        assertThatThrownBy(() -> ImageType.sniff(
                bytes('R', 'I', 'F', 'F', 1, 2, 3, 4, 'W', 'A', 'V', 'E')))
                .isInstanceOf(UnsupportedImageTypeException.class);
    }

    @Test
    void refusesAnSvgHoweverItIsNamed() {
        // An SVG served from our own origin is script execution in our own
        // security context - a stored XSS wearing a profile picture.
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>"
                .getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> ImageType.sniff(svg))
                .isInstanceOf(UnsupportedImageTypeException.class);
    }

    @Test
    void refusesHtmlThatCallsItselfAPng() {
        byte[] html = "<!doctype html><script>alert(1)</script>".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> ImageType.sniff(html))
                .isInstanceOf(UnsupportedImageTypeException.class);
    }

    @Test
    void refusesSomethingTooShortToIdentify() {
        assertThatThrownBy(() -> ImageType.sniff(bytes(0x89, 'P')))
                .isInstanceOf(UnsupportedImageTypeException.class);
        assertThatThrownBy(() -> ImageType.sniff(new byte[0]))
                .isInstanceOf(UnsupportedImageTypeException.class);
    }

    @Test
    void namesTheExtensionFromTheBytesRatherThanTheUpload() {
        assertThat(ImageType.PNG.extension()).isEqualTo("png");
        assertThat(ImageType.JPEG.extension()).isEqualTo("jpg");
        assertThat(ImageType.WEBP.extension()).isEqualTo("webp");
    }
}
