package nl.buevink.screens.mqtt;

public enum ScreenStatus {
    OPENING("opening"), CLOSING("closing"), OPEN("open"), CLOSED("closed"), STOPPED("stopped");

    private final String statusString;
    ScreenStatus(String statusString) {
        this.statusString = statusString;
    }

    public String getStatusString() {
        return statusString;
    }
}
