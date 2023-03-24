package nl.buevink.screens.control;

import lombok.Data;

import java.util.concurrent.Semaphore;

@Data
public class Screen {
    private String name;
    private int number;
    private int downSpeed;
    private int upSpeed;
    private final Semaphore semaphore = new Semaphore(1);
    private int currentHeight = 0;
}
