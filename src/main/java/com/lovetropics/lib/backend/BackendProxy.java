package com.lovetropics.lib.backend;

import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public final class BackendProxy implements BackendConnection {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Duration BASE_RECONNECT_INTERVAL = Duration.ofSeconds(10);
    private static final Duration MAX_RECONNECT_INTERVAL = Duration.ofMinutes(5);
    private static final Duration PING_INTERVAL = Duration.ofSeconds(2);

    @Nullable
    private BackendConnectionConfig connectionConfig;
    private final Handler receiver;

    @Nullable
    private volatile BackendWebSocketConnection connection;
    private volatile boolean connecting;

    private Instant lastConnectTime = Instant.EPOCH;
    private Instant lastPingTime = Instant.EPOCH;

    private Duration reconnectInterval = BASE_RECONNECT_INTERVAL;

    public BackendProxy(BackendConnection.Handler handler) {
        receiver = new Handler(handler);
    }

    public void connectWith(@Nullable BackendConnectionConfig config) {
        if (Objects.equals(connectionConfig, config)) {
            return;
        }

        connectionConfig = config;

        BackendWebSocketConnection connection = this.connection;
        this.connection = null;
        if (connection != null) {
            connection.close();
        }

        if (config != null) {
            initiateConnection(config);
        }
    }

    public void tick() {
        if (connecting) {
            return;
        }

        BackendWebSocketConnection connection = this.connection;
        Instant time = Instant.now();

        if (connection != null) {
            tickConnected(connection, time);
        } else {
            tickDisconnected(time);
        }
    }

    private void tickConnected(BackendWebSocketConnection connection, Instant time) {
        if (Duration.between(lastPingTime, time).compareTo(PING_INTERVAL) > 0) {
            lastPingTime = time;
            connection.ping();
        }
    }

    private void tickDisconnected(Instant time) {
        BackendConnectionConfig config = connectionConfig;
        if (config == null) {
            return;
        }
        if (Duration.between(lastConnectTime, time).compareTo(reconnectInterval) > 0) {
            initiateConnection(config);
        }
    }

    private void initiateConnection(BackendConnectionConfig config) {
        lastConnectTime = Instant.now();

        connecting = true;

        BackendWebSocketConnection.connect(config, receiver).handle((connection, throwable) -> {
            if (connection != null) {
                onConnectionOpen(config, connection);
            } else {
                onConnectionError(config, throwable);
            }
            return null;
        });
    }

    private void onConnectionOpen(BackendConnectionConfig config, BackendWebSocketConnection connection) {
        LOGGER.info("Successfully opened backend connection to {}", config.uri());
        this.connection = connection;
        connecting = false;
        reconnectInterval = BASE_RECONNECT_INTERVAL;
    }

    private void onConnectionError(BackendConnectionConfig config, Throwable throwable) {
        LOGGER.error("Failed to open backend connection to {}", config.uri(), throwable);
        closeConnection();
        reconnectInterval = reconnectInterval.multipliedBy(2);
        if (reconnectInterval.compareTo(MAX_RECONNECT_INTERVAL) > 0) {
            reconnectInterval = MAX_RECONNECT_INTERVAL;
        }
    }

    private void closeConnection() {
        connection = null;
        connecting = false;
        lastConnectTime = Instant.now();
    }

    @Override
    public boolean send(JsonObject payload) {
        BackendConnection connection = this.connection;
        if (connection != null) {
            return connection.send(payload);
        } else {
            return false;
        }
    }

    @Override
    public boolean isConnected() {
        return connection != null;
    }

    @Override
    public void close() {
        BackendWebSocketConnection connection = this.connection;
        this.connection = connection;
        if (connection != null) {
            connection.close();
        }
    }

    private class Handler implements BackendConnection.Handler {
        private final BackendConnection.Handler delegate;

        private Handler(BackendConnection.Handler delegate) {
            this.delegate = delegate;
        }

        @Override
        public void acceptOpened() {
            delegate.acceptOpened();
        }

        @Override
        public void acceptMessage(JsonObject payload) {
            delegate.acceptMessage(payload);
        }

        @Override
        public void acceptError(Throwable cause) {
            delegate.acceptError(cause);
            closeConnection();
        }

        @Override
        public void acceptClosed(int code, @Nullable String reason) {
            delegate.acceptClosed(code, reason);
            closeConnection();
        }
    }
}
