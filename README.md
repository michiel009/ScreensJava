# ScreensJava
This application will control my sun screen at home by sending commands to a remote. This remote is connected to the IO pins of a raspberry pi.
This meant for a Somfy IO remote that has only buttons to start and stop moving the screens in a direction. This application adds the possibility to set a height and let the application do the waiting and controlling.

Commands are received from a MQTT server. And events are published about the current status.

## Health endpoint
The application exposes `GET /health` on port `8080` (configurable with `healthPort` in `config/config.yaml`).
It returns `200 UP` when the application has successfully published its uptime over MQTT in the last minute, and `503 DOWN` otherwise.
The uptime is published every time the broker sends `$SYS/broker/uptime`, so the broker must publish `$SYS` topics and the MQTT user must be allowed to subscribe to them.

### Docker Compose
```yaml
services:
  screens:
    image: <your-screens-image>
    restart: unless-stopped
    environment:
      password: <mqtt-password>
    volumes:
      - ./config:/app/config
    ports:
      - "8080:8080" # only needed when the health endpoint must be reachable from outside the container
    healthcheck:
      test: ["CMD", "wget", "-q", "-O", "/dev/null", "http://localhost:8080/health"]
      interval: 30s
      timeout: 5s
      retries: 3
      start_period: 30s
```

`start_period` gives the application time to connect and receive the first uptime message before failed checks count, since the endpoint reports `DOWN` until then.
If you change `healthPort`, update the port in both `ports` and the `healthcheck` URL.
