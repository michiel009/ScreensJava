package nl.buevink.screens;

import java.time.Instant;

public class Health {
    private volatile Instant lastMqttHeartbeat;

    public Instant getLastMqttHeartbeat() {
        return lastMqttHeartbeat;
    }

    public void updateLastMqttHeartbeat() {
        lastMqttHeartbeat = Instant.now();
    }
}
