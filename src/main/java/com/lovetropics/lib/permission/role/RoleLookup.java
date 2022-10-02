package com.lovetropics.lib.permission.role;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nonnull;

public interface RoleLookup {
    RoleLookup EMPTY = new RoleLookup() {
        @Override
        @Nonnull
        public RoleReader byEntity(Entity entity) {
            return RoleReader.EMPTY;
        }

        @Override
        @Nonnull
        public RoleReader bySource(CommandSourceStack source) {
            return RoleReader.EMPTY;
        }
    };

    @Nonnull
    default RoleReader byPlayer(Player player) {
        return this.byEntity(player);
    }

    @Nonnull
    RoleReader byEntity(Entity entity);

    @Nonnull
    RoleReader bySource(CommandSourceStack source);
}
