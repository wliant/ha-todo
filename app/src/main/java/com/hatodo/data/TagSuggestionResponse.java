package com.hatodo.data;

/**
 * A tag the collection already uses, and how many entries carry it
 * (requirements/0006 R52).
 *
 * <p>The count is what makes the suggestion useful rather than merely present:
 * it is how a member tells the tag their household settled on from the one
 * somebody typed once.
 */
public record TagSuggestionResponse(String tag, long count) {
}
