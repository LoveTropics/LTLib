package com.lovetropics.lib.permission.role;

import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public interface RoleModifier {
    RoleModifier EMPTY = new RoleModifier() {
        @Override
        public boolean addRoleTo(UUID playerId, Role role) {
            return false;
        }

        @Override
        public boolean removeRoleFrom(UUID playerId, Role role) {
            return false;
        }
    };

    boolean addRoleTo(UUID playerId, Role role);

    boolean removeRoleFrom(UUID playerId, Role role);

    default boolean addRoleTo(ServerPlayer player, Role role) {
        return addRoleTo(player.getUUID(), role);
    }

    default boolean removeRoleFrom(ServerPlayer player, Role role) {
        return removeRoleFrom(player.getUUID(), role);
    }
}
