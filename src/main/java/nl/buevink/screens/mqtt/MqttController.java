package nl.buevink.screens.mqtt;

import lombok.extern.slf4j.Slf4j;
import nl.buevink.screens.control.Screen;
import org.eclipse.paho.client.mqttv3.*;

import java.util.UUID;

@Slf4j
public class MqttController {

    private static final String COMMAND_TOPIC = "screens/command/+/height";
    private static final String STATUS_TOPIC = "screens/status/%s/state";
    private static final String POSITION_TOPIC = "screens/status/%s/position";
    private static final String REMOTE_POSITION_TOPIC = "screens/status/remote/position";
    private static final String REMOTE_STATE_TOPIC = "screens/status/remote/state";
    private final IMqttClient client;

    public MqttController(String server, int port, String username, String password) throws MqttException {
        String clientId = UUID.randomUUID().toString();
        client = new MqttClient(String.format("tcp://%s:%s", server, port), clientId);
        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(true);
        options.setCleanSession(true);
        options.setConnectionTimeout(10);
        options.setUserName(username);
        options.setPassword(password.toCharArray());
        client.connect(options);
    }

    public void subscribeChangeHeight(ChangeHeightCommandListener commandListerner) throws MqttException {
        client.subscribe(COMMAND_TOPIC, commandListerner);
    }

    public void subscribeChangeRemote(ChangeRemoteCommandListener commandListerner) throws MqttException {
        client.subscribe(REMOTE_POSITION_TOPIC, commandListerner);
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


}
