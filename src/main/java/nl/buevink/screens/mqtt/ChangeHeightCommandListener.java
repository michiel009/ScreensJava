package nl.buevink.screens.mqtt;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.buevink.screens.control.RemoteController;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@AllArgsConstructor
public class ChangeHeightCommandListener implements IMqttMessageListener {
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final RemoteController remoteController;

    @Override
    public void messageArrived(String topic, MqttMessage msg) {
        String payload = new String(msg.getPayload());

        String nrStr = topic.split("/")[2];

        int number = Integer.parseInt(nrStr);
        int height = 100 - Integer.parseInt(payload);

        log.info("Received command: {}", payload);
        executor.submit(() -> {
            try {
                remoteController.changeHeight(number, height);
            } catch (InterruptedException e) {
                log.error("Failed command", e);
            } catch (MqttException e) {
                log.error("Failed processing mqtt action", e);
            }
        });

    }
}
