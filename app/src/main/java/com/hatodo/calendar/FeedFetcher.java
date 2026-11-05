package com.hatodo.calendar;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * The only place a feed is opened (docs/adr/0035).
 *
 * <p>Redirects are followed by hand rather than by the client, because the
 * guard has to run again on every hop: a public host that redirects to
 * {@code 127.0.0.1} is the whole trick, and {@code followRedirects(NORMAL)}
 * would take it without asking anybody.
 *
 * <p>The body is read through a counting stream that gives up past the cap,
 * rather than buffered and measured afterwards - a 2 GB response should cost
 * this process nothing.
 */
@Component
public class FeedFetcher {

    /** Generous for a calendar, nowhere near enough to hurt the heap. */
    private static final int MAX_BYTES = 5 * 1024 * 1024;
    private static final int MAX_REDIRECTS = 3;

    private final FeedUrlGuard guard;
    private final HttpClient http;

    @Autowired
    public FeedFetcher(FeedUrlGuard guard) {
        this(guard, HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(10))
                .build());
    }

    /** For a test that wants to answer without a socket. */
    FeedFetcher(FeedUrlGuard guard, HttpClient http) {
        this.guard = guard;
        this.http = http;
    }

    /**
     * @return the feed's bytes as text
     * @throws FeedUrlNotAllowedException if the address, or anything it
     *         redirects to, is one this server must not fetch
     * @throws FeedFetchException if it could not be read
     */
    public String fetch(URI url) {
        URI current = url;
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            guard.check(current);
            HttpResponse<InputStream> response = send(current);
            int status = response.statusCode();

            if (status >= 300 && status < 400) {
                current = redirectTarget(response, current);
                continue;
            }
            if (status == 404 || status == 410) {
                // The single most likely failure in the life of a feed: the
                // member regenerated the address at the provider.
                throw new FeedFetchException(
                        "The calendar was not found. Its address may have been regenerated.");
            }
            if (status == 401 || status == 403) {
                throw new FeedFetchException("The calendar refused to be read.");
            }
            if (status != 200) {
                throw new FeedFetchException("The calendar answered with an error (" + status + ").");
            }
            return read(response);
        }
        throw new FeedFetchException("That address redirects too many times.");
    }

    private HttpResponse<InputStream> send(URI url) {
        HttpRequest request = HttpRequest.newBuilder(url)
                .timeout(Duration.ofSeconds(20))
                // The declared type is not trusted either way: providers serve
                // .ics as text/calendar, text/plain and application/octet-stream
                // about equally, so the bytes decide.
                .header("Accept", "text/calendar, text/plain, */*")
                .GET()
                .build();
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (IOException unreachable) {
            throw new FeedFetchException("The calendar could not be reached.", unreachable);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new FeedFetchException("The refresh was interrupted.", interrupted);
        }
    }

    private URI redirectTarget(HttpResponse<InputStream> response, URI from) {
        String location = response.headers().firstValue("location")
                .orElseThrow(() -> new FeedFetchException("The calendar redirected to nowhere."));
        try {
            // Resolved against the current URL, because a Location may be a path.
            return from.resolve(new URI(location));
        } catch (URISyntaxException malformed) {
            throw new FeedFetchException("The calendar redirected to an address we cannot read.");
        }
    }

    private String read(HttpResponse<InputStream> response) {
        try (InputStream body = response.body()) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int read;
            while ((read = body.read(chunk)) != -1) {
                if (buffer.size() + read > MAX_BYTES) {
                    throw new FeedFetchException("That calendar is too large to read.");
                }
                buffer.write(chunk, 0, read);
            }
            // UTF-8 regardless of what the response claimed: RFC 5545 says
            // iCalendar is UTF-8, and a mislabelled charset would otherwise
            // turn an accented title into a parse failure.
            return buffer.toString(StandardCharsets.UTF_8);
        } catch (IOException truncated) {
            throw new FeedFetchException("The calendar stopped responding part-way.", truncated);
        }
    }
}
