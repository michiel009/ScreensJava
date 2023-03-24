package nl.buevink.screens.control;

import com.diozero.devices.Buzzer;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Semaphore;


@Slf4j
public class IORemote implements Remote {
    private static final int SLEEPTIMEDOWN = 100;
    private static final int SLEEPTIMEUP = 100;
    private static final int MAXPOSITION = 4;
    private final Semaphore semaphore = new Semaphore(1);
    private final Buzzer upButton;
    private final Buzzer downButton;
    private final Buzzer stopButton;
    private final Buzzer switchButton;
    
    @Getter
    @Setter
    private int position;

    public IORemote(int position) {
        this.position = position;
        upButton = new Buzzer(14, false);
        downButton = new Buzzer(2, false);
        stopButton = new Buzzer(3, false);
        switchButton = new Buzzer(4, false);
    }

    @Override
    public void pushUp() throws InterruptedException {
        log.info("Push Up while in position {}", getPosition());
        push(upButton);
    }

    @Override
    public void pushDown() throws InterruptedException {
        log.info("Push Down while in position {}", getPosition());
        push(downButton);
    }

    @Override
    public void pushStop() throws InterruptedException {
        log.info("Push Stop while in position {}", getPosition());
        push(stopButton);
    }

    @Override
    public void pushSwitchLightUp() throws InterruptedException {
        log.info("Push SwitchLightUp while in position {}", getPosition());
        push(switchButton);
    }

    @Override
    public int pushSwitch() throws InterruptedException {
        log.info("Push Switch while in position {}", getPosition());
        position++;
        if (position > MAXPOSITION)
            position = 0;
        push(switchButton);
        return position;
    }

    @Override
    public Semaphore getSemaphore() {
        return semaphore;
    }


    public void push(Buzzer button) throws InterruptedException {
        log.debug("Pushing button {}", button.getGpio());
        button.on();
        Thread.sleep(SLEEPTIMEDOWN);
        log.debug("Releasing button {}", button.getGpio());
        button.off();
        Thread.sleep(SLEEPTIMEUP);
        log.debug("Finished button {}", button.getGpio());
    }


}
