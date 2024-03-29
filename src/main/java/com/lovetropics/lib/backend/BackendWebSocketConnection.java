package com.lovetropics.lib.backend;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketClientCompressionHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.timeout.WriteTimeoutHandler;

import javax.net.ssl.SSLException;
import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public final class BackendWebSocketConnection extends SimpleChannelInboundHandler<WebSocketFrame> implements BackendConnection {
	private static final EventLoopGroup EVENT_LOOP_GROUP = new NioEventLoopGroup(
			1,
			new ThreadFactoryBuilder()
					.setNameFormat("lt-backend-event-loop")
					.setDaemon(true)
					.build()
	);

	private static final int TIMEOUT_SECONDS = 30;
	private static final int MAX_FRAME_SIZE = 16 * 1024 * 1024;

	private static final Gson GSON = new Gson();
	private static final JsonParser JSON_PARSER = new JsonParser();

	private final Handler handler;

	private final ConcurrentLinkedQueue<String> writeQueue = new ConcurrentLinkedQueue<>();
	private final AtomicBoolean scheduledWrite = new AtomicBoolean(false);

	private Channel channel;

	private BackendWebSocketConnection(Handler handler) {
		this.handler = handler;
	}

	public static CompletableFuture<BackendWebSocketConnection> connect(URI address, Handler handler) {
		String protocol = address.getScheme();
		if (!protocol.equals("ws") && !protocol.equals("wss")) {
			throw new IllegalArgumentException("Backend connection requires ws or wss protocol!");
		}

		BackendWebSocketConnection connection = new BackendWebSocketConnection(handler);

		HttpHeaders headers = new DefaultHttpHeaders();
		SslContext ssl;
		if (protocol.equals("wss")) {
			try {
				ssl = SslContextBuilder.forClient().build();
			} catch (SSLException e) {
				throw new RuntimeException(e);
			}
		} else {
			ssl = null;
		}

		WebSocketClientHandshaker handshaker = WebSocketClientHandshakerFactory.newHandshaker(address, WebSocketVersion.V13, null, false, headers, MAX_FRAME_SIZE);
		WebSocketClientProtocolHandler websocket = new WebSocketClientProtocolHandler(handshaker);

		Bootstrap bootstrap = new Bootstrap();
		bootstrap.group(EVENT_LOOP_GROUP);
		bootstrap.channel(NioSocketChannel.class);
		bootstrap.handler(new ChannelInitializer<SocketChannel>() {
			@Override
			protected void initChannel(SocketChannel channel) {
				channel.pipeline()
						.addLast(new WriteTimeoutHandler(TIMEOUT_SECONDS));
				if (ssl != null) {
					channel.pipeline().addLast(ssl.newHandler(channel.alloc(), address.getHost(), address.getPort()));
				}
				channel.pipeline().addLast(new HttpClientCodec())
						.addLast(new HttpObjectAggregator(MAX_FRAME_SIZE))
						.addLast(WebSocketClientCompressionHandler.INSTANCE)
						.addLast(websocket)
						.addLast(connection);
			}
		});

		CompletableFuture<Channel> future = awaitFuture(bootstrap.connect(address.getHost(), address.getPort()));

		future.handle((connected, error) -> {
			if (connected != null) {
				connection.channel = connected;
				connection.handler.acceptOpened();
			} else {
				connection.handler.acceptError(error);
			}
			return null;
		});

		return future.thenApply(c -> connection);
	}

	private static CompletableFuture<Channel> awaitFuture(ChannelFuture channelFuture) {
		CompletableFuture<Channel> completableFuture = new CompletableFuture<>();
		channelFuture.addListener((ChannelFutureListener) result -> {
			if (result.isSuccess()) {
				completableFuture.complete(result.channel());
			} else {
				Throwable cause = result.cause();
				completableFuture.completeExceptionally(cause);
			}
		});
		return completableFuture;
	}

	public void ping() {
		EVENT_LOOP_GROUP.execute(() -> this.channel.writeAndFlush(new PingWebSocketFrame()));
	}

	@Override
	public boolean send(JsonObject payload) {
		String text = GSON.toJson(payload);
		this.writeQueue.add(text);

		if (this.scheduledWrite.compareAndSet(false, true)) {
			EVENT_LOOP_GROUP.execute(this::writeQueued);
		}

		return true;
	}

	private void writeQueued() {
		this.scheduledWrite.set(false);

		ConcurrentLinkedQueue<String> writeQueue = this.writeQueue;
		if (!writeQueue.isEmpty()) {
			Channel channel = this.channel;

			String message;
			while ((message = writeQueue.poll()) != null) {
				ChannelFuture future = channel.write(new TextWebSocketFrame(message));
				future.addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
			}

			channel.flush();
		}
	}

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) {
		if (frame instanceof TextWebSocketFrame) {
			this.acceptTextFrame((TextWebSocketFrame) frame);
		} else if (frame instanceof CloseWebSocketFrame) {
			this.acceptCloseFrame((CloseWebSocketFrame) frame);
		}
	}

	private void acceptTextFrame(TextWebSocketFrame textFrame) {
		JsonObject payload = JSON_PARSER.parse(textFrame.text()).getAsJsonObject();
		this.handler.acceptMessage(payload);
	}

	private void acceptCloseFrame(CloseWebSocketFrame closeFrame) {
		if (this.channel != null) {
			this.handler.acceptClosed(closeFrame.statusCode(), closeFrame.reasonText());
			this.channel = null;
		}
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
		if (this.channel != null) {
			this.handler.acceptError(cause);
			this.channel.writeAndFlush(new CloseWebSocketFrame());
			ctx.close();

			this.channel = null;
		}
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) {
		if (this.channel != null) {
			this.handler.acceptClosed(-1, null);
			this.channel = null;
		}
	}

	@Override
	public boolean isConnected() {
		return this.channel != null;
	}
}
