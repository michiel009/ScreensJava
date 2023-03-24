package nl.buevink.screens.control;

import lombok.extern.slf4j.Slf4j;
import nl.buevink.screens.Store;
import nl.buevink.screens.mqtt.MqttController;
import nl.buevink.screens.mqtt.ScreenStatus;
import org.eclipse.paho.client.mqttv3.MqttException;

import java.util.List;

@Slf4j
public class RemoteController {
    private final Remote remote;
    private final List<Screen> screens;
    private final MqttController mqttController;
    private final Store store;

    public RemoteController(Remote remote, List<Screen> screens, MqttController mqttController, Store store) {
        this.remote = remote;
        this.screens = screens;
        this.mqttController = mqttController;
        this.store = store;
    }

    public void changeHeight(int screenNumber, int newHeight) throws InterruptedException, MqttException {

        log.info("In function");

        Screen screen =
                screens.stream().filter(screenf ->
                        screenf.getNumber() == screenNumber).findFirst().orElse(null);
        if (screen == null) {
            log.info("Screen number {} doesn't exists", screenNumber);
            return;
        }

        if (screen.getCurrentHeight() == newHeight) {
            log.info("New height matches current height for screen {} and height {}, no action",
                    screen.getName(), newHeight);

            mqttController.sendPosition(screen, newHeight);
            return;
        }

        log.info("Changing height of screen {} from {} to {}",
                screen.getName(), screen.getCurrentHeight(), newHeight);
        screen.getSemaphore().acquire();
        remote.getSemaphore().acquire();
        changeRemotePosition(screen);
        ScreenStatus endstate = ScreenStatus.STOPPED;

        if (screen.getCurrentHeight() < newHeight) {
            mqttController.sendStatus(screen, ScreenStatus.CLOSING);
            remote.pushDown();
            if (newHeight == 100) {
                remote.getSemaphore().release();
                waitGoingDown(screen, newHeight);
                endstate = ScreenStatus.CLOSED;
            } else {
                waitGoingDown(screen, newHeight);
                remote.pushStop();
                remote.getSemaphore().release();
            }
        } else {
            mqttController.sendStatus(screen, ScreenStatus.OPENING);
            remote.pushUp();
            if (newHeight == 0) {
                remote.getSemaphore().release();
                waitGoingUp(screen, newHeight);
                endstate = ScreenStatus.OPEN;
            } else {
                waitGoingUp(screen, newHeight);
                remote.pushStop();
                remote.getSemaphore().release();
            }
        }
        screen.setCurrentHeight(newHeight);
        mqttController.sendPosition(screen, newHeight);
        mqttController.sendStatus(screen, endstate);
        screen.getSemaphore().release();
    }

    private void changeRemotePosition(Screen screen) throws MqttException, InterruptedException {

        if (remote.getPosition() != screen.getNumber()) {
            Thread.sleep(1000);
            remote.pushSwitchLightUp();
            while (remote.pushSwitch() != screen.getNumber()) ;
        }
        mqttController.sendRemotePosition(remote.getPosition());
        store.storeRemotePosition(remote.getPosition());
    }

    private void waitGoingDown(Screen screen, int newHeight) throws InterruptedException {
        long waitingTime = (long) (newHeight - screen.getCurrentHeight()) * screen.getDownSpeed();
        if (newHeight == 100)
            waitingTime += 100;
        log.info("Going to wait for {}ms to change screen {} to new height: {}",
                waitingTime, screen.getName(), newHeight);

        Thread.sleep(waitingTime);
    }

    private void waitGoingUp(Screen screen, int newHeight) throws InterruptedException {
        long waitingTime = (long) (screen.getCurrentHeight() - newHeight) * screen.getUpSpeed();
        if (newHeight == 0)
            waitingTime += 100;
        log.info("Going to wait for {}ms to change screen {} to new height: {}",
                waitingTime, screen.getName(), newHeight);

        Thread.sleep(waitingTime);
    }

    public void setRemotePosition(int position) throws InterruptedException, MqttException {
        remote.getSemaphore().acquire();
        log.info("Going to change the remote position from {} to {}", remote.getPosition(), position);
        remote.setPosition(position);
        mqttController.sendRemotePosition(position);
        store.storeRemotePosition(position);
        remote.getSemaphore().release();
    }

}
