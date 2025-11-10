package com.lovetropics.lib.backend;

import com.google.gson.JsonObject;

import javax.annotation.Nullable;

public interface BackendConnection extends AutoCloseable {
    boolean send(JsonObject payload);

    boolean isConnected();

    @Override
    void close();

    interface Handler {
        void acceptOpened();

        void acceptMessage(JsonObject payload);

        void acceptError(Throwable cause);

        void acceptClosed(int code, @Nullable String reason);
    }
}
