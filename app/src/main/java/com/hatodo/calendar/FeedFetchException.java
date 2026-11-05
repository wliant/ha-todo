package com.hatodo.calendar;

/**
 * The feed could not be read, with a reason short enough to show the member.
 *
 * <p>The message is the one stored on the feed and rendered on a screen, so it
 * is a phrase rather than a diagnostic: never an exception class, never the
 * URL, and never anything echoed from the response body.
 */
public class FeedFetchException extends RuntimeException {

    public FeedFetchException(String message) {
        super(message);
    }

    public FeedFetchException(String message, Throwable cause) {
        super(message, cause);
    }
}
