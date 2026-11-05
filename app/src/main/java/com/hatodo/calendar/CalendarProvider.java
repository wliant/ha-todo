package com.hatodo.calendar;

import java.net.URI;

/**
 * Which service published a feed, inferred from its host.
 *
 * <p>Display only. Nothing in the fetch, the parse or the store branches on
 * this: one parser reads every provider, which is most of what adr/0034 buys.
 * It exists so a member with four feeds can tell them apart at a glance, and so
 * the add-a-calendar screen can say "that looks like a Google calendar".
 */
public enum CalendarProvider {
    GOOGLE,
    OUTLOOK,
    OTHER;

    public static CalendarProvider of(URI url) {
        String host = url.getHost() == null ? "" : url.getHost().toLowerCase();
        if (host.endsWith("google.com") || host.endsWith("googleusercontent.com")) {
            return GOOGLE;
        }
        if (host.endsWith("outlook.com") || host.endsWith("outlook.office.com")
                || host.endsWith("office365.com") || host.endsWith("live.com")) {
            return OUTLOOK;
        }
        return OTHER;
    }
}
