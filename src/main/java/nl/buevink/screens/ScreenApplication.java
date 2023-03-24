package nl.buevink.screens;

import lombok.extern.slf4j.Slf4j;
import nl.buevink.screens.control.*;
import nl.buevink.screens.mqtt.ChangeHeightCommandListener;
import nl.buevink.screens.mqtt.ChangeRemoteCommandListener;
import nl.buevink.screens.mqtt.MqttController;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.List;

@Slf4j
public class ScreenApplication {
    private static List<Screen> screens;
    private static RemoteController remoteController;
    private static ChangeHeightCommandListener changeHeightCommandListener;
    private static ChangeRemoteCommandListener changeRemoteCommandListener;
    private static MqttController mqttController;
    private static Store store;
    private static Remote remote;

    public static void main(String[] args) throws InterruptedException, MqttException, FileNotFoundException {
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "INFO");
        Config config = getConfig();
        store = new Store();
        remote = config.isDryRun() ? new RemoteMock() : new IORemote(store.restoreRemotePosition());
        mqttController = new MqttController(config.getMqttServer(), config.getMqttPort(), config.getMqttUsername(), config.getMqttPassword());

        screens = config.getScreens();

        remoteController = new RemoteController(remote, screens, mqttController, store);
        remoteController.setRemotePosition(store.restoreRemotePosition());


        changeHeightCommandListener = new ChangeHeightCommandListener(remoteController);
        changeRemoteCommandListener = new ChangeRemoteCommandListener(remoteController);
        mqttController.subscribeChangeHeight(changeHeightCommandListener);
        mqttController.subscribeChangeRemote(changeRemoteCommandListener);
        log.info("Application started");
    }

    private static Config getConfig() throws FileNotFoundException {
        LoaderOptions options = new LoaderOptions();
        Yaml yaml = new Yaml(new Constructor(Config.class, options));
        InputStream inputStream = new FileInputStream("config/config.yaml");
        Config config = yaml.load(inputStream);
        if(System.getenv("password") != null)
            config.setMqttPassword(config.getMqttPassword().replace("${password}", System.getenv("password")));
        return config;
    }
}
