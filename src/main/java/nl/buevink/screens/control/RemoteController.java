package nl.buevink.screens.control;

import lombok.extern.slf4j.Slf4j;
import nl.buevink.screens.Store;
import nl.buevink.screens.mqtt.MqttController;
import nl.buevink.screens.mqtt.ScreenStatus;
import org.eclipse.paho.client.mqttv3.MqttException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Slf4j
public class RemoteController {
    private final Remote remote;
    private final List<Screen> screens;
    private final MqttController mqttController;
    private final Store store;

    // Guarded by commandLock: the newest command per screen and the movement running per screen
    private final Object commandLock = new Object();
    private final Map<Integer, Long> latestCommands = new HashMap<>();
    private final Map<Integer, Movement> movements = new HashMap<>();
    private long nextCommand = 0;

    private record Movement(long command, int target, CountDownLatch interruption) {
    }

    public RemoteController(Remote remote, List<Screen> screens, MqttController mqttController, Store store) {
        this.remote = remote;
        this.screens = screens;
        this.mqttController = mqttController;
        this.store = store;
    }

    public void changeHeight(int screenNumber, int newHeight) throws InterruptedException, MqttException {

        Screen screen =
                screens.stream().filter(screenf ->
                        screenf.getNumber() == screenNumber).findFirst().orElse(null);
        if (screen == null) {
            log.info("Screen number {} doesn't exists", screenNumber);
            return;
        }

        Long command = registerCommand(screen, newHeight);
        if (command == null)
            return;

        screen.getSemaphore().acquire();
        try {
            moveScreen(screen, newHeight, command);
        } finally {
            screen.getSemaphore().release();
        }
    }

    /**
     * Registers a new command for the screen and interrupts the movement that is running for it.
     *
     * @return the command id, or null when the screen is already moving to the new height
     */
    private Long registerCommand(Screen screen, int newHeight) {
        synchronized (commandLock) {
            Movement movement = movements.get(screen.getNumber());
            if (movement != null && movement.target() == newHeight
                    && movement.command() == latestCommands.get(screen.getNumber())) {
                log.info("Screen {} is already moving to height {}, no action", screen.getName(), newHeight);
                return null;
            }

            long command = nextCommand++;
            latestCommands.put(screen.getNumber(), command);
            if (movement != null) {
                log.info("Interrupting current movement of screen {}", screen.getName());
                movement.interruption().countDown();
            }
            return command;
        }
    }

    /**
     * @return the movement for this command, or null when a newer command for the screen is pending
     */
    private Movement startMovement(Screen screen, int newHeight, long command) {
        synchronized (commandLock) {
            if (latestCommands.get(screen.getNumber()) != command)
                return null;
            Movement movement = new Movement(command, newHeight, new CountDownLatch(1));
            movements.put(screen.getNumber(), movement);
            return movement;
        }
    }

    private void endMovement(Screen screen, Movement movement) {
        synchronized (commandLock) {
            movements.remove(screen.getNumber(), movement);
        }
    }

    private void moveScreen(Screen screen, int newHeight, long command) throws InterruptedException, MqttException {
        Movement movement = startMovement(screen, newHeight, command);
        if (movement == null) {
            log.info("Skipping command to change screen {} to height {}, a newer command is pending",
                    screen.getName(), newHeight);
            return;
        }

        try {
            if (screen.getCurrentHeight() == newHeight) {
                log.info("New height matches current height for screen {} and height {}, no action",
                        screen.getName(), newHeight);

                mqttController.sendPosition(screen, newHeight);
                return;
            }

            log.info("Changing height of screen {} from {} to {}",
                    screen.getName(), screen.getCurrentHeight(), newHeight);
            boolean goingDown = screen.getCurrentHeight() < newHeight;
            boolean fullMove = newHeight == (goingDown ? 100 : 0);
            boolean interrupted = false;
            long elapsed = 0;
            long start;

            remote.getSemaphore().acquire();
            try {
                changeRemotePosition(screen);
                start = pushMove(screen, goingDown);
                if (!fullMove) {
                    interrupted = waitForMovement(screen, newHeight, goingDown, movement.interruption());
                    elapsed = System.nanoTime() - start;
                    remote.pushStop();
                }
            } finally {
                remote.getSemaphore().release();
            }

            if (fullMove) {
                interrupted = waitForMovement(screen, newHeight, goingDown, movement.interruption());
                if (interrupted)
                    elapsed = stopScreen(screen, start);
            }

            int reachedHeight = interrupted ? heightAfter(screen, goingDown, elapsed) : newHeight;
            ScreenStatus endstate = ScreenStatus.STOPPED;
            if (reachedHeight == 100)
                endstate = ScreenStatus.CLOSED;
            else if (reachedHeight == 0)
                endstate = ScreenStatus.OPEN;

            screen.setCurrentHeight(reachedHeight);
            mqttController.sendPosition(screen, reachedHeight);
            mqttController.sendStatus(screen, endstate);
        } finally {
            endMovement(screen, movement);
        }
    }

    /**
     * Pushes the up or down button, the remote must be held and set to the screen.
     *
     * @return the System.nanoTime() at which the screen started moving
     */
    private long pushMove(Screen screen, boolean goingDown) throws InterruptedException, MqttException {
        mqttController.sendStatus(screen, goingDown ? ScreenStatus.CLOSING : ScreenStatus.OPENING);
        long start = System.nanoTime();
        if (goingDown)
            remote.pushDown();
        else
            remote.pushUp();
        return start;
    }

    /**
     * Reacquires the remote to stop a screen that was moving without holding the remote.
     *
     * @return the nanoseconds the screen moved since start
     */
    private long stopScreen(Screen screen, long start) throws InterruptedException, MqttException {
        log.info("Reacquiring remote to stop screen {}", screen.getName());
        remote.getSemaphore().acquire();
        try {
            changeRemotePosition(screen);
            long elapsed = System.nanoTime() - start;
            remote.pushStop();
            return elapsed;
        } finally {
            remote.getSemaphore().release();
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

    private int heightAfter(Screen screen, boolean goingDown, long elapsedNanos) {
        double elapsedMillis = elapsedNanos / 1_000_000.0;
        int moved = (int) Math.round(elapsedMillis / (goingDown ? screen.getDownSpeed() : screen.getUpSpeed()));
        int height = goingDown ? screen.getCurrentHeight() + moved : screen.getCurrentHeight() - moved;
        return Math.max(0, Math.min(100, height));
    }

    public void setRemotePosition(int position) throws InterruptedException, MqttException {
        remote.getSemaphore().acquire();
        try {
            log.info("Going to change the remote position from {} to {}", remote.getPosition(), position);
            remote.setPosition(position);
            mqttController.sendRemotePosition(position);
            store.storeRemotePosition(position);
        } finally {
            remote.getSemaphore().release();
        }
    }

}
