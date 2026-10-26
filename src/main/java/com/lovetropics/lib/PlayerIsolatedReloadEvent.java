package com.lovetropics.lib;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/// Fired when a [ServerPlayer] is reloaded when entering or exiting an isolated game.
public class PlayerIsolatedReloadEvent extends PlayerEvent {
    public PlayerIsolatedReloadEvent(ServerPlayer player) {
        super(player);
    }

    @Override
    public ServerPlayer getEntity() {
        return (ServerPlayer) super.getEntity();
    }
}
