package com.hatodo;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A calendar publisher, on this machine.
 *
 * <p>This is {@code adr/0034}'s testability claim being cashed in: a feed is a
 * text file over HTTP, so the whole subscribe-fetch-parse-store path can be
 * exercised for real - including the cases that break it. The OAuth version of
 * this feature could not be tested end to end at all without mocking two
 * providers' token endpoints.
 *
 * <p>{@code com.sun.net.httpserver} rather than a dependency: it is in the JDK,
 * and this needs to answer a GET with a string.
 */
final class FixtureFeedServer implements AutoCloseable {

    private final HttpServer server;
    private final Map<String, Handler> routes = new ConcurrentHashMap<>();

    interface Handler {
        void handle(HttpExchange exchange) throws IOException;
    }

    FixtureFeedServer() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException cannotBind) {
            throw new IllegalStateException("fixture feed server could not start", cannotBind);
        }
        server.createContext("/", exchange -> {
            Handler handler = routes.get(exchange.getRequestURI().getPath());
            if (handler == null) {
                send(exchange, 404, "text/plain", "no such calendar");
                return;
            }
            handler.handle(exchange);
        });
        server.start();
    }

    /** @return the address to subscribe */
    String serving(String path, String body) {
        routes.put(path, exchange -> send(exchange, 200, "text/calendar", body));
        return url(path);
    }

    String answering(String path, int status, String body) {
        routes.put(path, exchange -> send(exchange, status, "text/plain", body));
        return url(path);
    }

    /** A public-looking address that redirects to loopback: the guard's real test. */
    String redirectingTo(String path, String location) {
        routes.put(path, exchange -> {
            exchange.getResponseHeaders().add("Location", location);
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        return url(path);
    }

    /** Larger than the fetcher's cap, written a chunk at a time. */
    String servingTooMuch(String path) {
        routes.put(path, exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "text/calendar");
            exchange.sendResponseHeaders(200, 0);
            byte[] chunk = "BEGIN:VEVENT\nSUMMARY:padding padding padding\nEND:VEVENT\n"
                    .repeat(100).getBytes(StandardCharsets.UTF_8);
            try (OutputStream body = exchange.getResponseBody()) {
                for (int written = 0; written < 6 * 1024 * 1024; written += chunk.length) {
                    body.write(chunk);
                }
            } catch (IOException expected) {
                // The fetcher gave up part-way, which is the point.
            }
            exchange.close();
        });
        return url(path);
    }

    String url(String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    private static void send(HttpExchange exchange, int status, String type, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", type);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
        exchange.close();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
