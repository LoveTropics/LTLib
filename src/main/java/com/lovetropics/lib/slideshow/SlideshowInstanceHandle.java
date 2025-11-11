package com.lovetropics.lib.slideshow;

import net.minecraft.server.level.ServerPlayer;

public interface SlideshowInstanceHandle {
    void addPlayer(ServerPlayer player);

    void removePlayer(ServerPlayer player);

    default void play() {
        setPaused(false);
    }

    default void pause() {
        setPaused(true);
    }

    default void setPaused(boolean paused) {
        seekTo(currentTime(), paused);
    }

    void seekTo(double time, boolean paused);

    double currentTime();

    double totalTime();

    boolean isPaused();

    void close();
}
