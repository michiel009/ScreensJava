package nl.buevink.screens.control;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Semaphore;

@Slf4j
@Getter
public class RemoteMock implements Remote {

    private static final int MAXPOSITION = 4;
    private final Semaphore semaphore = new Semaphore(1);

    @Setter
    private int position;

    public RemoteMock() {
        this.position = 0;
    }

    @Override
    public void pushUp() {
        log.info("Push Up");
    }

    @Override
    public void pushDown() {
        log.info("Push Down");
    }

    @Override
    public void pushStop() {
        log.info("Push Stop");
    }

    @Override
    public void pushSwitchLightUp() {
        log.info("Push SwitchLightUp");
    }

    @Override
    public int pushSwitch() {
        log.info("Push Switch");
        position++;
        if (position > MAXPOSITION)
            position = 0;

        return position;

    }
}
