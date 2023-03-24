package nl.buevink.screens.control;

import java.util.concurrent.Semaphore;

public interface Remote {
    void pushUp() throws InterruptedException;

    void pushDown() throws InterruptedException;

    void pushStop() throws InterruptedException;

    void pushSwitchLightUp() throws InterruptedException;

    int pushSwitch() throws InterruptedException;

    int getPosition();

    void setPosition(int position);

    Semaphore getSemaphore();
}
