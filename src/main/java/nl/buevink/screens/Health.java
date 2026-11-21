package nl.buevink.screens;

import java.time.Instant;

public class Health {
    private volatile Instant mqttConnectionTime;

    public Instant getMqttConnectionTime() {
        return mqttConnectionTime;
    }

    public void updateMqttConnectionTime() {
        mqttConnectionTime = Instant.now();
    }
}
