package nl.buevink.screens;

import lombok.Data;
import nl.buevink.screens.control.Screen;

import java.util.List;

@Data
public class Config {
    private boolean dryRun = true;
    private List<Screen> screens;
    private String mqttServer;
    private int mqttPort;
    private String mqttUsername;
    private String mqttPassword;
}
