package com.lovetropics.lib.slideshow;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.net.URI;

public interface SlideshowManager {
    @Nullable
    SlideshowInstanceHandle open(ResourceLocation id);

    void preload(ServerPlayer player, ResourceLocation id);

    void replacePlayer(ServerPlayer oldPlayer, ServerPlayer newPlayer);

    @Nullable
    default ResourceLocation importSimpleVideo(ResourceLocation name, URI url, double duration) {
        return null;
    }
}
