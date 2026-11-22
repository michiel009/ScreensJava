package nl.buevink.screens.control;

import lombok.extern.slf4j.Slf4j;
import nl.buevink.screens.Store;
import nl.buevink.screens.mqtt.MqttController;
import nl.buevink.screens.mqtt.ScreenStatus;
import org.eclipse.paho.client.mqttv3.MqttException;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Slf4j
public class RemoteController {
    private final Remote remote;
    private final List<Screen> screens;
    private final MqttController mqttController;
    private final Store store;
    private final Map<Integer, CountDownLatch> movements = new ConcurrentHashMap<>();

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

        interruptMovement(screen);
        screen.getSemaphore().acquire();
        try {
            moveScreen(screen, newHeight);
        } finally {
            screen.getSemaphore().release();
        }
    }

    private void interruptMovement(Screen screen) {
        CountDownLatch movement = movements.get(screen.getNumber());
        if (movement != null) {
            log.info("Interrupting current movement of screen {}", screen.getName());
            movement.countDown();
        }
    }

    private void moveScreen(Screen screen, int newHeight) throws InterruptedException, MqttException {
        if (screen.getCurrentHeight() == newHeight) {
            log.info("New height matches current height for screen {} and height {}, no action",
                    screen.getName(), newHeight);

            mqttController.sendPosition(screen, newHeight);
            return;
        }

        log.info("Changing height of screen {} from {} to {}",
                screen.getName(), screen.getCurrentHeight(), newHeight);
        CountDownLatch interruption = new CountDownLatch(1);
        movements.put(screen.getNumber(), interruption);
        try {
            remote.getSemaphore().acquire();
            changeRemotePosition(screen);

            boolean goingDown = screen.getCurrentHeight() < newHeight;
            boolean fullMove = newHeight == (goingDown ? 100 : 0);
            if (goingDown) {
                mqttController.sendStatus(screen, ScreenStatus.CLOSING);
                remote.pushDown();
            } else {
                mqttController.sendStatus(screen, ScreenStatus.OPENING);
                remote.pushUp();
            }
            long start = System.currentTimeMillis();
            if (fullMove)
                remote.getSemaphore().release();

            boolean interrupted = waitForMovement(screen, newHeight, goingDown, interruption);

            int reachedHeight = newHeight;
            ScreenStatus endstate = ScreenStatus.STOPPED;
            if (fullMove && !interrupted) {
                endstate = goingDown ? ScreenStatus.CLOSED : ScreenStatus.OPEN;
            } else {
                if (fullMove) {
                    log.info("Reacquiring remote to stop screen {}", screen.getName());
                    remote.getSemaphore().acquire();
                    changeRemotePosition(screen);
                }
                long elapsed = System.currentTimeMillis() - start;
                remote.pushStop();
                remote.getSemaphore().release();
                if (interrupted)
                    reachedHeight = heightAfter(screen, goingDown, elapsed);
            }

            screen.setCurrentHeight(reachedHeight);
            mqttController.sendPosition(screen, reachedHeight);
            mqttController.sendStatus(screen, endstate);
        } finally {
            movements.remove(screen.getNumber(), interruption);
        }
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

    /**
     * Waits until the screen reached the new height.
     *
     * @return true when the movement got interrupted by a new command for the same screen
     */
    private boolean waitForMovement(Screen screen, int newHeight, boolean goingDown, CountDownLatch interruption)
            throws InterruptedException {
        long waitingTime = goingDown
                ? (long) (newHeight - screen.getCurrentHeight()) * screen.getDownSpeed()
                : (long) (screen.getCurrentHeight() - newHeight) * screen.getUpSpeed();
        if (newHeight == 100 || newHeight == 0)
            waitingTime += 100;
        log.info("Going to wait for {}ms to change screen {} to new height: {}",
                waitingTime, screen.getName(), newHeight);

        boolean interrupted = interruption.await(waitingTime, TimeUnit.MILLISECONDS);
        if (interrupted)
            log.info("Movement of screen {} to height {} interrupted", screen.getName(), newHeight);
        return interrupted;
    }

    private int heightAfter(Screen screen, boolean goingDown, long elapsed) {
        int moved = (int) (elapsed / (goingDown ? screen.getDownSpeed() : screen.getUpSpeed()));
        int height = goingDown ? screen.getCurrentHeight() + moved : screen.getCurrentHeight() - moved;
        return Math.max(0, Math.min(100, height));
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
