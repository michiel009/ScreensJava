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
    private static final Duration MAX_MQTT_AGE = Duration.ofMinutes(1);
    private final Health health;
    private final HttpServer server;

    public HealthServer(int port, Health health) throws IOException {
        this.health = health;
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/health", this::handleHealth);
    }

    public void start() {
        server.start();
        log.info("Health endpoint started on port {}", server.getAddress().getPort());
    }

    private void handleHealth(HttpExchange exchange) throws IOException {
        Instant mqttConnectionTime = health.getMqttConnectionTime();
        boolean healthy = mqttConnectionTime != null
                && mqttConnectionTime.isAfter(Instant.now().minus(MAX_MQTT_AGE));

        byte[] body = (healthy ? "UP" : "DOWN").getBytes();
        exchange.getResponseHeaders().set("Content-Type", "text/plain");
        exchange.sendResponseHeaders(healthy ? 200 : 503, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }
}
