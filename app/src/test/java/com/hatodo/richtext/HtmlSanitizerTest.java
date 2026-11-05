package com.hatodo.richtext;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Named attacks rather than a general "is it safe", because the client renders
 * this with dangerouslySetInnerHTML and is exactly as safe as this class is
 * correct.
 */
class HtmlSanitizerTest {

    private final HtmlSanitizer sanitizer = new HtmlSanitizer();

    @Test
    void stripsAScriptTag() {
        String clean = sanitizer.sanitize("<p>Hello</p><script>alert(1)</script>");

        assertThat(clean).contains("Hello").doesNotContain("script").doesNotContain("alert");
    }

    @Test
    void stripsAnEventHandler() {
        String clean = sanitizer.sanitize("<p onclick=\"alert(1)\">Tap me</p>");

        assertThat(clean).contains("Tap me").doesNotContain("onclick");
    }

    @Test
    void stripsAJavascriptLink() {
        String clean = sanitizer.sanitize("<a href=\"javascript:alert(1)\">go</a>");

        assertThat(clean).doesNotContain("javascript:");
    }

    @Test
    void stripsAnImageThatWouldRunOnError() {
        assertThat(sanitizer.sanitize("<img src=x onerror=alert(1)>")).isNull();
    }

    @Test
    void keepsOnlyPicturesFromOurOwnEndpoint() {
        String ours = "/api/images/0f8fad5b-d9cb-469f-a165-70867728950e";
        String clean = sanitizer.sanitize(
                "<p>a</p><img src=\"%s\" alt=\"mine\"><img src=\"https://evil.test/x.png\">"
                        .formatted(ours));

        assertThat(clean).contains(ours).doesNotContain("evil.test");
    }

    @Test
    void refusesADataUrlImage() {
        String clean = sanitizer.sanitize(
                "<p>a</p><img src=\"data:image/svg+xml;base64,PHN2Zz48L3N2Zz4=\">");

        assertThat(clean).doesNotContain("data:");
    }

    @Test
    void stripsAnInlineFrameAndAnSvg() {
        String clean = sanitizer.sanitize(
                "<p>text</p><iframe src=\"https://evil.test\"></iframe><svg><script/></svg>");

        assertThat(clean).contains("text").doesNotContain("iframe").doesNotContain("svg");
    }

    @Test
    void stripsStyleAndClassSoNothingCanRepaintTheApp() {
        String clean = sanitizer.sanitize(
                "<p style=\"position:fixed;inset:0\" class=\"overlay\">text</p>");

        assertThat(clean).contains("text").doesNotContain("style").doesNotContain("class");
    }

    @Test
    void keepsTheFormattingSomebodyActuallyWroteWith() {
        String clean = sanitizer.sanitize(
                "<h3>Steps</h3><ol><li><strong>Heat</strong> the oven</li>"
                        + "<li><em>Then</em> wait</li></ol>");

        assertThat(clean)
                .contains("<h3>").contains("<ol>").contains("<li>")
                .contains("<strong>").contains("<em>")
                .contains("Heat").contains("wait");
    }

    @Test
    void marksOutboundLinksSoNothingGetsAHandleBackOnTheApp() {
        String clean = sanitizer.sanitize("<a href=\"https://example.com\">a recipe</a>");

        assertThat(clean).contains("href=\"https://example.com\"").contains("nofollow");
    }

    @Test
    void treatsWhatIsLeftOfAnEmptyEditorAsNoDetailAtAll() {
        // So "nothing written" has one representation rather than several.
        assertThat(sanitizer.sanitize(null)).isNull();
        assertThat(sanitizer.sanitize("   ")).isNull();
        assertThat(sanitizer.sanitize("<p></p>")).isNull();
        assertThat(sanitizer.sanitize("<p><br></p>")).isNull();
        assertThat(sanitizer.sanitize("<script>alert(1)</script>")).isNull();
    }
}
