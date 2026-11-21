package nl.buevink.screens.mqtt;

import lombok.extern.slf4j.Slf4j;
import nl.buevink.screens.control.Screen;
import org.eclipse.paho.client.mqttv3.*;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
public class MqttController {

    private static final String COMMAND_TOPIC = "screens/command/+/height";
    private static final String STATUS_TOPIC = "screens/status/%s/state";
    private static final String POSITION_TOPIC = "screens/status/%s/position";
    private static final String REMOTE_POSITION_TOPIC = "screens/status/remote/position";
    private static final String REMOTE_STATE_TOPIC = "screens/status/remote/state";
    private static final String BROKER_UPTIME_TOPIC = "$SYS/broker/uptime";
    private static final String UPTIME_TOPIC = "screens/status/uptime";
    private final IMqttClient client;
    private final Map<String, IMqttMessageListener> subscriptions = new ConcurrentHashMap<>();
    private final ExecutorService resubscribeExecutor = Executors.newSingleThreadExecutor();

    public MqttController(String server, int port, String username, String password) throws MqttException {
        String clientId = UUID.randomUUID().toString();
        client = new MqttClient(String.format("tcp://%s:%s", server, port), clientId);
        client.setCallback(new ReconnectCallback());
        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(true);
        options.setCleanSession(true);
        options.setConnectionTimeout(10);
        options.setUserName(username);
        options.setPassword(password.toCharArray());
        client.connect(options);
    }

    public void subscribeChangeHeight(ChangeHeightCommandListener commandListerner) throws MqttException {
        subscribe(COMMAND_TOPIC, commandListerner);
    }

    public void subscribeChangeRemote(ChangeRemoteCommandListener commandListerner) throws MqttException {
        subscribe(REMOTE_POSITION_TOPIC, commandListerner);
    }

    public void subscribeBrokerUptime(BrokerUptimeListener uptimeListener) throws MqttException {
        subscribe(BROKER_UPTIME_TOPIC, uptimeListener);
    }

    private void subscribe(String topic, IMqttMessageListener listener) throws MqttException {
        subscriptions.put(topic, listener);
        client.subscribe(topic, listener);
    }

    // With a clean session the broker forgets subscriptions on reconnect, so they are restored here.
    // This runs on a separate thread because blocking client calls must not be made from a Paho callback.
    private void resubscribe() {
        subscriptions.forEach((topic, listener) -> {
            try {
                client.subscribe(topic, listener);
                log.info("Resubscribed to {}", topic);
            } catch (MqttException e) {
                log.error("Failed resubscribing to {}", topic, e);
            }
        });
    }

    public void sendUptime(String uptime) throws MqttException {
        client.publish(UPTIME_TOPIC, new MqttMessage(uptime.getBytes()));
    }

    public void sendStatus(Screen screen, ScreenStatus status) throws MqttException {
        MqttMessage mqttMessage = new MqttMessage(status.getStatusString().getBytes());
        mqttMessage.setRetained(true);
        client.publish(String.format(STATUS_TOPIC, screen.getNumber()), mqttMessage);
    }

    public void sendPosition(Screen screen, int position) throws MqttException {
        MqttMessage mqttMessage = new MqttMessage(Integer.toString(100 - position).getBytes());
        mqttMessage.setRetained(true);
        client.publish(String.format(POSITION_TOPIC, screen.getNumber()), mqttMessage);
    }

    public void sendRemotePosition(int position) throws MqttException {
        MqttMessage mqttMessage = new MqttMessage(Integer.toString(position).getBytes());
        mqttMessage.setRetained(true);
        client.publish(REMOTE_STATE_TOPIC, mqttMessage);
    }

    private class ReconnectCallback implements MqttCallbackExtended {
        @Override
        public void connectComplete(boolean reconnect, String serverURI) {
            if (reconnect) {
                log.info("Reconnected to {}", serverURI);
                resubscribeExecutor.execute(MqttController.this::resubscribe);
            }
        }

        @Override
        public void connectionLost(Throwable cause) {
            log.warn("MQTT connection lost", cause);
        }

        @Override
        public void messageArrived(String topic, MqttMessage message) {
        }

        @Override
        public void deliveryComplete(IMqttDeliveryToken token) {
        }
    }
}
