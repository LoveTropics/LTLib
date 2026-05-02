package com.lovetropics.lib.slideshow;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import org.jspecify.annotations.Nullable;
import java.net.URI;

public final class SlideshowApi {
    private static SlideshowManager slideshowManager = new SlideshowManager() {
        @Override
        public @Nullable SlideshowInstanceHandle open(Identifier id) {
            return null;
        }

        @Override
        public void preload(ServerPlayer player, Identifier id) {
        }

        @Override
        public void replacePlayer(ServerPlayer oldPlayer, ServerPlayer newPlayer) {
        }
    };

    public static void setSlideshowManager(SlideshowManager slideshowManager) {
        SlideshowApi.slideshowManager = slideshowManager;
    }

    public @Nullable static SlideshowInstanceHandle open(Identifier id) {
        return slideshowManager.open(id);
    }

    public static void preload(ServerPlayer player, Identifier id) {
        slideshowManager.preload(player, id);
    }

    public static void replacePlayer(ServerPlayer oldPlayer, ServerPlayer newPlayer) {
        slideshowManager.replacePlayer(oldPlayer, newPlayer);
    }

    public static @Nullable Identifier importSimpleVideo(Identifier id, URI url, double duration) {
        return slideshowManager.importSimpleVideo(id, url, duration);
    }
}
