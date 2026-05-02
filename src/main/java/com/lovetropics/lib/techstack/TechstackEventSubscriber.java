package com.lovetropics.lib.techstack;

import com.google.gson.JsonElement;
import com.google.gson.JsonSyntaxException;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.net.URI;
import java.util.function.Consumer;

public final class TechstackEventSubscriber implements AutoCloseable {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final WsConnectionManager connectionManager;

    private TechstackEventSubscriber(URI uri, EventProcessor eventProcessor, Runnable onConnectionOpen, @Nullable String token) {
        connectionManager = new WsConnectionManager(uri, eventProcessor.subscriptionKeys(), token, new WsConnectionManager.EventHandler() {
            @Override
            public void handleConnectionOpen() {
                onConnectionOpen.run();
            }

            @Override
            public void handleEvent(JsonElement eventJson) {
                try {
                    eventProcessor.parseAndHandle(eventJson);
                } catch (JsonSyntaxException e) {
                    LOGGER.error("Failed to parse event {} with error: {}", e.getMessage(), eventJson);
                }
            }
        });
    }

    public static Builder builder(URI uri) {
        return new Builder(uri);
    }

    public boolean isConnected() {
        return connectionManager.isConnected();
    }

    @Override
    public void close() {
        connectionManager.close();
    }

    public static class Builder {
        private final URI uri;
        private final EventProcessor.Builder eventProcessor = new EventProcessor.Builder();
        private Runnable onConnectionOpen = () -> {};
        private @Nullable String token;

        private Builder(URI uri) {
            this.uri = uri;
        }

        public <T> Builder subscribe(Crud crud, String id, Codec<T> codec, Consumer<T> handler) {
            eventProcessor.subscribe(crud, id, codec, handler);
            return this;
        }

        public Builder onConnectionOpen(Runnable onConnectionOpen) {
            this.onConnectionOpen = onConnectionOpen;
            return this;
        }

        public Builder authenticate(@Nullable String token) {
            this.token = token;
            return this;
        }

        public TechstackEventSubscriber build() {
            return new TechstackEventSubscriber(uri, eventProcessor.build(), onConnectionOpen, token);
        }
    }
}
