package com.hatodo.richtext;

import java.util.regex.Pattern;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.springframework.stereotype.Component;

/**
 * The only thing standing between an item's detail field and a stored
 * cross-site scripting bug.
 *
 * <p>Runs on write, server-side, so what is persisted is already safe and no
 * read path has to remember to filter. The client renders the result directly
 * and sanitizes nothing of its own: a second implementation of one rule drifts
 * from the first, and the one that matters is this one. See docs/adr/0018.
 */
@Component
public class HtmlSanitizer {

    /**
     * Images may only come from our own image endpoint.
     *
     * <p>One rule, doing a lot: it rejects {@code javascript:}, {@code data:}
     * and off-site sources at once, and it means every picture in an item went
     * through the uploader - which is what makes that image's own access check
     * meaningful.
     */
    private static final Pattern OWN_IMAGES =
            Pattern.compile("^/api/images/[0-9a-fA-F-]{36}$");

    private static final PolicyFactory POLICY = new HtmlPolicyBuilder()
            .allowElements("p", "br", "strong", "em", "u", "s", "code", "pre",
                    "blockquote", "h2", "h3", "ul", "ol", "li", "a", "img")
            .allowAttributes("href").onElements("a")
            .allowUrlProtocols("http", "https", "mailto")
            // Nothing opened from here gets a handle back on this window.
            .requireRelNofollowOnLinks()
            .allowAttributes("src", "alt").matching((element, attribute, value) ->
                    "src".equals(attribute) && OWN_IMAGES.matcher(value).matches() ? value
                            : "alt".equals(attribute) ? value : null)
            .onElements("img")
            .toFactory();

    /**
     * @return the sanitized HTML, or null when nothing survived - so "no
     *         detail" has exactly one representation rather than two
     */
    public String sanitize(String html) {
        if (html == null || html.isBlank()) {
            return null;
        }
        String clean = POLICY.sanitize(html);
        return clean.isBlank() || stripTags(clean).isBlank() && !clean.contains("<img") ? null : clean;
    }

    private static String stripTags(String html) {
        return html.replaceAll("<[^>]*>", "").replace("&nbsp;", " ").trim();
    }
}
