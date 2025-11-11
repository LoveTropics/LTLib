package com.lovetropics.lib.slideshow;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;

public interface SlideshowManager {
    @Nullable
    SlideshowInstanceHandle open(ResourceLocation id);

    void preload(ServerPlayer player, ResourceLocation id);

    void replacePlayer(ServerPlayer oldPlayer, ServerPlayer newPlayer);
}
