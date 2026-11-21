package nl.buevink.screens;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;

@Slf4j
public class HealthServer {
    private static final String HEALTH_PATH = "/health";
    private static final Duration MAX_HEARTBEAT_AGE = Duration.ofMinutes(1);
    private final Health health;
    private final HttpServer server;

    public HealthServer(int port, Health health) throws IOException {
        this.health = health;
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext(HEALTH_PATH, this::handleHealth);
    }

    public void start() {
        server.start();
        log.info("Health endpoint started on port {}", server.getAddress().getPort());
    }

    private void handleHealth(HttpExchange exchange) throws IOException {
        try (exchange) {
            if (!HEALTH_PATH.equals(exchange.getRequestURI().getPath())) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }

            String method = exchange.getRequestMethod();
            if (!method.equals("GET") && !method.equals("HEAD")) {
                exchange.getResponseHeaders().set("Allow", "GET, HEAD");
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            Instant lastMqttHeartbeat = health.getLastMqttHeartbeat();
            boolean healthy = lastMqttHeartbeat != null
                    && lastMqttHeartbeat.isAfter(Instant.now().minus(MAX_HEARTBEAT_AGE));
            int status = healthy ? 200 : 503;

            exchange.getResponseHeaders().set("Content-Type", "text/plain");
            if (method.equals("HEAD")) {
                exchange.sendResponseHeaders(status, -1);
                return;
            }

            byte[] body = (healthy ? "UP" : "DOWN").getBytes();
            exchange.sendResponseHeaders(status, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        }
    }
}
