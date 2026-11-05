package com.hatodo.richtext;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Which of our own pictures a piece of rich text refers to.
 *
 * <p>One implementation, shared by everything that owns a detail field. The
 * pattern has to agree with {@link HtmlSanitizer}'s {@code img src} rule - that
 * rule is what guarantees every picture in stored HTML went through our own
 * uploader, and this is what turns those references into a claim. Two copies of
 * it would eventually disagree about which is authoritative.
 */
@Component
public class DetailImages {

    private static final Pattern OWN_IMAGE =
            Pattern.compile("/api/images/([0-9a-fA-F-]{36})");

    /**
     * @param html any number of rich-text bodies, nulls allowed - an owner may
     *             have more than one field carrying pictures, and a claim is
     *             keyed on the owner rather than on which field referred to it
     * @return the referenced ids, de-duplicated, in the order first seen
     */
    public Set<UUID> referencedIn(String... html) {
        Set<UUID> referenced = new LinkedHashSet<>();
        for (String body : html) {
            if (body == null) {
                continue;
            }
            Matcher matcher = OWN_IMAGE.matcher(body);
            while (matcher.find()) {
                try {
                    referenced.add(UUID.fromString(matcher.group(1)));
                } catch (IllegalArgumentException e) {
                    // 36 hex-and-dash characters that are not a UUID. Not a
                    // picture of ours, so not a claim; and not worth failing a
                    // write that the sanitizer already passed.
                }
            }
        }
        return referenced;
    }
}
