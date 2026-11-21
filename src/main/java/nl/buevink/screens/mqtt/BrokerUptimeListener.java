package nl.buevink.screens.mqtt;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.buevink.screens.Health;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.lang.management.ManagementFactory;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@AllArgsConstructor
public class BrokerUptimeListener implements IMqttMessageListener {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final MqttController mqttController;
    private final Health health;

    @Override
    public void messageArrived(String topic, MqttMessage msg) {
        executor.execute(() -> {
            try {
                long uptimeSeconds = ManagementFactory.getRuntimeMXBean().getUptime() / 1000;
                mqttController.sendUptime(Long.toString(uptimeSeconds));
                health.updateLastMqttHeartbeat();
            } catch (MqttException e) {
                log.error("Failed sending uptime", e);
            } catch (RuntimeException e) {
                log.error("Unexpected error sending uptime", e);
            }
        });
    }
}
