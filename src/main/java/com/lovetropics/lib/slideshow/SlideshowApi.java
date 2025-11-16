package com.lovetropics.lib.slideshow;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.net.URI;

public final class SlideshowApi {
    private static SlideshowManager slideshowManager = new SlideshowManager() {
        @Override
        public @Nullable SlideshowInstanceHandle open(ResourceLocation id) {
            return null;
        }

        @Override
        public void preload(ServerPlayer player, ResourceLocation id) {
        }

        @Override
        public void replacePlayer(ServerPlayer oldPlayer, ServerPlayer newPlayer) {
        }
    };

    public static void setSlideshowManager(SlideshowManager slideshowManager) {
        SlideshowApi.slideshowManager = slideshowManager;
    }

    @Nullable
    public static SlideshowInstanceHandle open(ResourceLocation id) {
        return slideshowManager.open(id);
    }

    public static void preload(ServerPlayer player, ResourceLocation id) {
        slideshowManager.preload(player, id);
    }

    public static void replacePlayer(ServerPlayer oldPlayer, ServerPlayer newPlayer) {
        slideshowManager.replacePlayer(oldPlayer, newPlayer);
    }

    @Nullable
    public static ResourceLocation importSimpleVideo(ResourceLocation id, URI url, double duration) {
        return slideshowManager.importSimpleVideo(id, url, duration);
    }
}
