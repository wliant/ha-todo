package com.hatodo.calendar;

/**
 * The address is not one this server will fetch.
 *
 * <p>Carries a reason the member can act on and <b>nothing about what was found
 * at the address</b> - the whole point of the guard is that the response is not
 * reachable, and a message distinguishing "connection refused" from "no route"
 * is a port scanner. See docs/adr/0035.
 */
public class FeedUrlNotAllowedException extends RuntimeException {

    public FeedUrlNotAllowedException(String message) {
        super(message);
    }
}
