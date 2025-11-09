package com.lovetropics.lib.permission.role;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nonnull;

public interface RoleLookup {
    RoleLookup EMPTY = new RoleLookup() {
        @Override
        public RoleReader byEntity(Entity entity) {
            return RoleReader.EMPTY;
        }

        @Override
        public RoleReader bySource(CommandSourceStack source) {
            return RoleReader.EMPTY;
        }
    };

    default RoleReader byPlayer(Player player) {
        return this.byEntity(player);
    }

    RoleReader byEntity(Entity entity);

    RoleReader bySource(CommandSourceStack source);
}
