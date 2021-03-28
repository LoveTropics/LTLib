package com.lovetropics.lib.backend;

import com.google.gson.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.net.URI;
import java.util.function.Supplier;

public final class BackendProxy implements BackendConnection {
	private static final Logger LOGGER = LogManager.getLogger(BackendProxy.class);
	private static final long RECONNECT_INTERVAL_MS = 10 * 1000;
	private static final long PING_INTERVAL_MS = 2 * 1000;

	private final Supplier<URI> address;
	private final Handler receiver;

	private volatile BackendWebSocketConnection connection;
	private volatile boolean connecting;

	private long lastConnectTime;
	private long lastPingTime;

	public BackendProxy(Supplier<URI> address, BackendConnection.Handler handler) {
		this.address = address;
		this.receiver = new Handler(handler);
		this.initiateConnection();
	}

	public void tick() {
		if (this.connecting) {
			return;
		}

		BackendWebSocketConnection connection = this.connection;
		long time = System.currentTimeMillis();

		if (connection != null) {
			this.tickConnected(connection, time);
		} else {
			this.tickDisconnected(time);
		}
	}

	private void tickConnected(BackendWebSocketConnection connection, long time) {
		if (time - this.lastPingTime > PING_INTERVAL_MS) {
			this.lastPingTime = time;
			connection.ping();
		}
	}

	private void tickDisconnected(long time) {
		if (time - this.lastConnectTime > RECONNECT_INTERVAL_MS) {
			this.initiateConnection();
		}
	}

	private void initiateConnection() {
		this.lastConnectTime = System.currentTimeMillis();

		URI address = this.address.get();
		if (address != null) {
			this.connecting = true;

			BackendWebSocketConnection.connect(address, this.receiver).handle((connection, throwable) -> {
				if (connection != null) {
					this.onConnectionOpen(connection);
				} else {
					this.onConnectionError(throwable);
				}
				return null;
			});
		}
	}

	private void onConnectionOpen(BackendWebSocketConnection connection) {
		LOGGER.info("Successfully opened backend connection to {}", this.address);
		this.connection = connection;
	}

	private void onConnectionError(Throwable throwable) {
		LOGGER.error("Failed to open backend connection to {}", this.address, throwable);
		this.closeConnection();
	}

	private void closeConnection() {
		this.connection = null;
		this.connecting = false;
		this.lastConnectTime = System.currentTimeMillis();
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
		return this.connection != null;
	}

	private class Handler implements BackendConnection.Handler {
		private final BackendConnection.Handler delegate;

		private Handler(BackendConnection.Handler delegate) {
			this.delegate = delegate;
		}

		@Override
		public void acceptOpened() {
			this.delegate.acceptOpened();
		}

		@Override
		public void acceptMessage(JsonObject payload) {
			this.delegate.acceptMessage(payload);
		}

		@Override
		public void acceptError(Throwable cause) {
			this.delegate.acceptError(cause);
			BackendProxy.this.closeConnection();
		}

		@Override
		public void acceptClosed(int code, @Nullable String reason) {
			this.delegate.acceptClosed(code, reason);
			BackendProxy.this.closeConnection();
		}
	}
}
