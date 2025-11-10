package com.lovetropics.lib.backend;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketClientProtocolHandler;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketClientCompressionHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import javax.net.ssl.SSLException;
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
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final int TIMEOUT_SECONDS = 30;
    private static final int MAX_FRAME_SIZE = 16 * 1024 * 1024;

    private static final Gson GSON = new Gson();

    private final Handler handler;

    private final ConcurrentLinkedQueue<String> writeQueue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean scheduledWrite = new AtomicBoolean(false);

    @Nullable
    private Channel channel;

    private BackendWebSocketConnection(Handler handler) {
        this.handler = handler;
    }

    public static CompletableFuture<BackendWebSocketConnection> connect(BackendConnectionConfig config, Handler handler) {
        String protocol = config.uri().getScheme();
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

        String host = config.uri().getHost();
        int port = config.uri().getPort();

        WebSocketClientHandshaker handshaker = WebSocketClientHandshakerFactory.newHandshaker(config.decoratedUri(), WebSocketVersion.V13, null, false, headers, MAX_FRAME_SIZE);
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
                    channel.pipeline().addLast(ssl.newHandler(channel.alloc(), host, port));
                }
                channel.pipeline().addLast(new HttpClientCodec())
                        .addLast(new HttpObjectAggregator(MAX_FRAME_SIZE))
                        .addLast(WebSocketClientCompressionHandler.INSTANCE)
                        .addLast(websocket)
                        .addLast(connection);
            }
        });

        CompletableFuture<Channel> future = awaitFuture(bootstrap.connect(host, port));

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
        EVENT_LOOP_GROUP.execute(() -> {
            Channel channel = this.channel;
            if (channel != null) {
                channel.writeAndFlush(new PingWebSocketFrame());
            }
        });
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
            if (channel == null) {
                return;
            }

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
        try {
            JsonObject payload = JsonParser.parseString(textFrame.text()).getAsJsonObject();
            this.handler.acceptMessage(payload);
        } catch (Exception e) {
            LOGGER.error("An exception occurred while handling event: {}", textFrame.text(), e);
        }
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

    @Override
    public void close() {
        Channel channel = this.channel;
        this.channel = null;
        if (channel != null) {
            channel.close();
        }
    }
}
