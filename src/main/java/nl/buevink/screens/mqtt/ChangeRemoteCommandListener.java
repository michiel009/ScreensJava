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
public class ChangeRemoteCommandListener implements IMqttMessageListener  {
    private final ExecutorService executor
            = Executors.newSingleThreadExecutor();
    private final RemoteController remoteController;

    @Override
    public void messageArrived(String topic, MqttMessage msg) {

        int position = Integer.parseInt(new String(msg.getPayload()));

        log.info("Received remote position: {}", position);
        executor.submit(() -> {
            try {
                remoteController.setRemotePosition(position);
            } catch (InterruptedException e) {
                log.error("Failed command",e);
            } catch (MqttException e) {
                log.error("Failed processing mqtt action",e);
            }
        });

    }
}
