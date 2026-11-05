package com.hatodo.data;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * The rules a tag obeys (requirements/0006 R52).
 *
 * <p>Enforced here rather than in SQL because SQL cannot state them: "every
 * element of this array is lowercase and non-blank" has no {@code CHECK} form
 * without a trigger, and the project has no triggers. This is the same argument
 * adr/0022 makes about the always-declared due date living in
 * {@code ListFieldService.normalise}.
 *
 * <p>What the database <em>can</em> state, it does: no NULL element, and at
 * most {@link #MAX_TAGS} of them.
 *
 * <p>Pure, so it is unit-testable without a database or a Spring context.
 */
@Component
public class TagNormaliser {

    static final int MAX_TAGS = 20;
    static final int MAX_LENGTH = 40;

    /**
     * Lowercased so "Quick" and "quick" are one tag rather than two that look
     * identical in a chip row; sorted so the stored array and {@code tags_text}
     * are stable, which keeps an edit that changes nothing from moving the
     * version.
     *
     * @throws InvalidTagException with a sentence a member can act on
     */
    public List<String> normalise(List<String> raw) {
        Set<String> seen = new LinkedHashSet<>();
        for (String tag : raw) {
            if (tag == null) {
                continue;
            }
            String cleaned = tag.trim().toLowerCase(java.util.Locale.ROOT);
            // Internal whitespace collapses: "slow  cooker" and "slow cooker"
            // are the same tag, and the second is what anyone would type next.
            cleaned = cleaned.replaceAll("\\s+", " ");
            if (cleaned.isEmpty()) {
                continue;
            }
            if (cleaned.length() > MAX_LENGTH) {
                throw new InvalidTagException(
                        "\"%s\" is too long for a tag. Keep tags under %d characters."
                                .formatted(tag.trim(), MAX_LENGTH));
            }
            seen.add(cleaned);
        }
        if (seen.size() > MAX_TAGS) {
            throw new InvalidTagException(
                    "That is more than %d tags. Pick the ones you would search for."
                            .formatted(MAX_TAGS));
        }
        List<String> sorted = new ArrayList<>(seen);
        sorted.sort(String::compareTo);
        return List.copyOf(sorted);
    }
}
