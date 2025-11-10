package com.lovetropics.lib.techstack;

import com.google.gson.JsonElement;
import com.google.gson.JsonSyntaxException;
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
import io.netty.handler.codec.http.websocketx.WebSocketCloseStatus;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketClientCompressionHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.timeout.WriteTimeoutHandler;
import net.minecraft.util.LenientJsonParser;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import javax.net.ssl.SSLException;
import java.net.URI;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/* package-private */ class WsConnection extends SimpleChannelInboundHandler<WebSocketFrame> implements AutoCloseable {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Set<String> ALLOWED_PROTOCOLS = Set.of("ws", "wss");
    private static final Duration WRITE_TIMEOUT = Duration.ofSeconds(30);
    private static final int MAX_FRAME_SIZE = 16 * 1024 * 1024;

    private static final EventLoopGroup EVENT_LOOP_GROUP = new NioEventLoopGroup(
            1,
            Thread.ofPlatform().name("lt-techstack-ws-netty").daemon().factory()
    );

    private final Handler handler;
    // Should only be accessed from event loop thread
    @Nullable
    private Channel channel;

    private final AtomicBoolean requestedClose = new AtomicBoolean();

    private WsConnection(Handler handler) {
        this.handler = handler;
    }

    public static CompletableFuture<WsConnection> connect(URI uri, Handler handler) {
        String protocol = uri.getScheme();
        if (!ALLOWED_PROTOCOLS.contains(protocol)) {
            throw new IllegalArgumentException("Backend connection requires ws or wss protocol!");
        }

        WsConnection connection = new WsConnection(handler);

        HttpHeaders headers = new DefaultHttpHeaders();
        SslContext sslContext = protocol.equals("wss") ? buildSslContext() : null;

        String host = uri.getHost();
        int port = uri.getPort();

        WebSocketClientHandshaker handshaker = WebSocketClientHandshakerFactory.newHandshaker(uri, WebSocketVersion.V13, null, false, headers, MAX_FRAME_SIZE);
        WebSocketClientProtocolHandler websocket = new WebSocketClientProtocolHandler(handshaker);

        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(EVENT_LOOP_GROUP);
        bootstrap.channel(NioSocketChannel.class);
        bootstrap.handler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel channel) {
                channel.pipeline().addLast(new WriteTimeoutHandler(WRITE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS));
                if (sslContext != null) {
                    channel.pipeline().addLast(sslContext.newHandler(channel.alloc(), host, port));
                }
                channel.pipeline().addLast(new HttpClientCodec())
                        .addLast(new HttpObjectAggregator(MAX_FRAME_SIZE))
                        .addLast(WebSocketClientCompressionHandler.INSTANCE)
                        .addLast(websocket)
                        .addLast(connection);
            }
        });

        return awaitFuture(bootstrap.connect(host, port)).thenApply(channel -> {
            connection.channel = channel;
            return connection;
        });
    }

    private static SslContext buildSslContext() {
        try {
            return SslContextBuilder.forClient().build();
        } catch (SSLException e) {
            throw new RuntimeException(e);
        }
    }

    private static CompletableFuture<Channel> awaitFuture(ChannelFuture channelFuture) {
        CompletableFuture<Channel> completableFuture = new CompletableFuture<>();
        channelFuture.addListener((ChannelFutureListener) result -> {
            if (result.isSuccess()) {
                completableFuture.complete(result.channel());
            } else {
                completableFuture.completeExceptionally(result.cause());
            }
        });
        return completableFuture;
    }

    public void sendPing() {
        EVENT_LOOP_GROUP.execute(() -> {
            if (channel != null) {
                channel.writeAndFlush(new PingWebSocketFrame());
            }
        });
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) {
        switch (frame) {
            case TextWebSocketFrame textFrame -> acceptTextFrame(textFrame);
            case CloseWebSocketFrame closeFrame -> acceptCloseFrame(closeFrame);
            default -> {
            }
        }
    }

    private void acceptTextFrame(TextWebSocketFrame textFrame) {
        try {
            handler.handleEvent(LenientJsonParser.parse(textFrame.text()));
        } catch (JsonSyntaxException e) {
            LOGGER.error("Failed to parse JSON frame: {}", textFrame.text(), e);
        }
    }

    private void acceptCloseFrame(CloseWebSocketFrame closeFrame) {
        if (clearChannel() != null) {
            handler.handleClosed(closeFrame.statusCode(), closeFrame.reasonText());
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        Channel channel = clearChannel();
        if (channel == null) {
            return;
        }
        handler.handleError(cause);
        channel.writeAndFlush(new CloseWebSocketFrame());
        ctx.close();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (clearChannel() != null) {
            handler.handleClosed(WebSocketCloseStatus.NORMAL_CLOSURE.code(), null);
        }
    }

    @Override
    public void close() {
        if (requestedClose.compareAndSet(false, true)) {
            EVENT_LOOP_GROUP.execute(() -> {
                Channel channel = clearChannel();
                if (channel != null) {
                    channel.writeAndFlush(new CloseWebSocketFrame());
                    channel.close();
                }
            });
        }
    }

    private @Nullable Channel clearChannel() {
        Channel channel = this.channel;
        this.channel = null;
        return channel;
    }

    public interface Handler {
        void handleEvent(JsonElement eventJson);

        void handleError(Throwable cause);

        void handleClosed(int code, @Nullable String reason);
    }
}
