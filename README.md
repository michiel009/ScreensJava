# ScreensJava
This application will control my sun screen at home by sending commands to a remote. This remote is connected to the IO pins of a raspberry pi.
This meant for a Somfy IO remote that has only buttons to start and stop moving the screens in a direction. This application adds the possibility to set a height and let the application do the waiting and controlling.

Commands are received from a MQTT server. And events are published about the current status.
