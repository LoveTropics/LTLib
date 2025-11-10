package com.lovetropics.lib.techstack;

import com.google.gson.JsonElement;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/* package-private */ final class WsConnectionManager implements AutoCloseable {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Duration BASE_RECONNECT_INTERVAL = Duration.ofSeconds(10);
    private static final Duration MAX_RECONNECT_INTERVAL = Duration.ofMinutes(5);

    private static final Duration TICK_INTERVAL = Duration.ofSeconds(2);

    private static final ScheduledExecutorService EXECUTOR = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon().name("lt-techstack-ws-manager").factory()
    );

    private final URI uri;
    private final URI decoratedUri;
    private final EventHandler eventHandler;

    private final ScheduledFuture<?> tickingFuture;

    private CompletableFuture<Optional<WsConnection>> connection = CompletableFuture.completedFuture(Optional.empty());

    private Instant lastConnectTime = Instant.EPOCH;
    private Duration reconnectInterval = BASE_RECONNECT_INTERVAL;

    private boolean closed;

    public WsConnectionManager(URI uri, Collection<String> subscriptionKeys, @Nullable String token, EventHandler eventHandler) {
        this.uri = uri;
        decoratedUri = decorateUri(uri, subscriptionKeys, token);
        this.eventHandler = eventHandler;

        tickingFuture = EXECUTOR.scheduleAtFixedRate(this::tick, 0, TICK_INTERVAL.toMillis(), TimeUnit.MILLISECONDS);
    }

    private static URI decorateUri(URI uri, Collection<String> subscriptionKeys, @Nullable String token) {
        if (subscriptionKeys.isEmpty() && token == null) {
            return uri;
        }

        List<Pair<String, String>> parameters = new ArrayList<>();
        for (String subscription : subscriptionKeys) {
            parameters.add(Pair.of("sub", subscription));
        }
        if (token != null) {
            parameters.add(Pair.of("token", token));
        }
        return appendQueryParameters(uri, parameters);
    }

    private static URI appendQueryParameters(URI uri, List<Pair<String, String>> parameters) {
        StringBuilder query = new StringBuilder(Objects.requireNonNullElse(uri.getQuery(), ""));
        for (Pair<String, String> parameter : parameters) {
            if (!query.isEmpty()) {
                query.append("&");
            }
            query.append(parameter.getFirst()).append("=").append(URLEncoder.encode(parameter.getSecond(), StandardCharsets.UTF_8));
        }

        try {
            return new URI(uri.getScheme(), uri.getUserInfo(), uri.getHost(), uri.getPort(), uri.getPath(), query.toString(), uri.getFragment());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("OptionalAssignedToNull")
    private void tick() {
        if (closed) {
            connection.thenAccept(maybeConnection -> maybeConnection.ifPresent(WsConnection::close));
            connection = CompletableFuture.completedFuture(Optional.empty());
            tickingFuture.cancel(false);
            return;
        }
        Optional<WsConnection> connection = this.connection.getNow(null);
        if (connection == null) {
            return;
        }
        if (connection.isPresent()) {
            tickConnected(connection.get());
        } else {
            tickDisconnected();
        }
    }

    private void tickConnected(WsConnection connection) {
        connection.sendPing();
    }

    private void tickDisconnected() {
        Instant time = Instant.now();
        if (Duration.between(lastConnectTime, time).compareTo(reconnectInterval) < 0) {
            return;
        }
        lastConnectTime = time;

        connection = WsConnection.connect(decoratedUri, new ConnectionHandler()).handle((connection, throwable) -> {
            eventHandler.handleConnectionOpen();
            lastConnectTime = Instant.now();
            if (throwable != null) {
                LOGGER.error("Failed to open techstack connection to {}", uri, throwable);
                reconnectInterval = growReconnectInterval(reconnectInterval);
            } else {
                LOGGER.info("Successfully opened techstack connection to {}", uri);
                reconnectInterval = BASE_RECONNECT_INTERVAL;
            }
            return Optional.ofNullable(connection);
        });
    }

    private static Duration growReconnectInterval(Duration reconnectInterval) {
        reconnectInterval = reconnectInterval.multipliedBy(2);
        if (reconnectInterval.compareTo(MAX_RECONNECT_INTERVAL) > 0) {
            reconnectInterval = MAX_RECONNECT_INTERVAL;
        }
        return reconnectInterval;
    }

    private void onConnectionClosed() {
        connection = CompletableFuture.completedFuture(Optional.empty());
        lastConnectTime = Instant.now();
    }

    @Override
    public void close() {
        closed = true;
    }

    public boolean isConnected() {
        return !closed && connection.getNow(Optional.empty()).isPresent();
    }

    public interface EventHandler {
        void handleConnectionOpen();

        void handleEvent(JsonElement eventJson);
    }

    private class ConnectionHandler implements WsConnection.Handler {
        @Override
        public void handleEvent(JsonElement eventJson) {
            LOGGER.debug("Received event from techstack: {}", eventJson);
            eventHandler.handleEvent(eventJson);
        }

        @Override
        public void handleError(Throwable cause) {
            LOGGER.error("An error occurred in the techstack connection", cause);
            EXECUTOR.submit(WsConnectionManager.this::onConnectionClosed);
        }

        @Override
        public void handleClosed(int code, @Nullable String reason) {
            LOGGER.error("Techstack websocket was closed with code {} and reason {}", code, reason);
            EXECUTOR.submit(WsConnectionManager.this::onConnectionClosed);
        }
    }
}
