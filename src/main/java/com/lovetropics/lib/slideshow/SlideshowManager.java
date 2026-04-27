package com.lovetropics.lib.slideshow;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.net.URI;

public interface SlideshowManager {
    @Nullable
    SlideshowInstanceHandle open(Identifier id);

    void preload(ServerPlayer player, Identifier id);

    void replacePlayer(ServerPlayer oldPlayer, ServerPlayer newPlayer);

    @Nullable
    default Identifier importSimpleVideo(Identifier name, URI url, double duration) {
        return null;
    }
}
