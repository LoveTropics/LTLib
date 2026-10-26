package com.lovetropics.lib.permission;

import com.google.common.collect.MapMaker;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.config.NeoForgeServerConfig;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import net.neoforged.neoforge.server.permission.handler.IPermissionHandler;
import net.neoforged.neoforge.server.permission.handler.IPermissionHandlerFactory;
import net.neoforged.neoforge.server.permission.nodes.PermissionDynamicContext;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/// Wrapper for Neo's [IPermissionHandler], enabling composition between multiple permission handlers.
/// Some handlers may not care to modify every permission node - this enables opt-in for specific nodes, otherwise falling through to the next handler.
public interface PermissionNodeHandler {
    static void register(PermissionGatherEvent.Handler event, int priority, PermissionNodeHandler handler) {
        NeoHandler.register(event, priority, handler);
    }

    <T> @Nullable T getPermission(ServerPlayer player, PermissionNode<T> node, PermissionDynamicContext<?>... context);

    <T> @Nullable T getOfflinePermission(UUID player, PermissionNode<T> node, PermissionDynamicContext<?>... context);

    @ApiStatus.Internal
    class NeoHandler implements IPermissionHandler {
        private static final Identifier ID = Identifier.fromNamespaceAndPath("ltlib", "permission_handler");

        private static final Logger LOGGER = LogUtils.getLogger();
        private static final Map<PermissionGatherEvent.Handler, Builder> BUILDERS = new MapMaker().weakKeys().makeMap();

        private final List<PermissionNodeHandler> handlers;
        private final Set<PermissionNode<?>> nodes;

        private NeoHandler(Set<PermissionNode<?>> nodes, List<PermissionNodeHandler> handlers) {
            this.nodes = nodes;
            this.handlers = handlers;
        }

        private static void register(PermissionGatherEvent.Handler event, int priority, PermissionNodeHandler handler) {
            setupDefaultPermissionHandler();
            BUILDERS.computeIfAbsent(event, e -> {
                Builder builder = new Builder();
                e.addPermissionHandler(ID, builder);
                return builder;
            }).add(priority, handler);
        }

        private static void setupDefaultPermissionHandler() {
            ModConfigSpec.ConfigValue<String> permissionHandler = NeoForgeServerConfig.INSTANCE.permissionHandler;
            String currentPermissionHandler = permissionHandler.get();
            if (currentPermissionHandler.equals(permissionHandler.getDefault())) {
                LOGGER.info("Permission handler is default in config, overriding");
                permissionHandler.set(ID.toString());
                permissionHandler.save();
            } else if (!currentPermissionHandler.equals(ID.toString())) {
                LOGGER.warn("Permission handler is already set in config to {}, role permission overrides will not work", currentPermissionHandler);
            }
        }

        @Override
        public Identifier getIdentifier() {
            return ID;
        }

        @Override
        public Set<PermissionNode<?>> getRegisteredNodes() {
            return nodes;
        }

        @Override
        public <T> T getPermission(ServerPlayer player, PermissionNode<T> node, PermissionDynamicContext<?>... context) {
            for (PermissionNodeHandler handler : handlers) {
                T value = handler.getPermission(player, node, context);
                if (value != null) {
                    return value;
                }
            }
            return node.getDefaultResolver().resolve(player, player.getUUID(), context);
        }

        @Override
        public <T> T getOfflinePermission(UUID player, PermissionNode<T> node, PermissionDynamicContext<?>... context) {
            for (PermissionNodeHandler handler : handlers) {
                T value = handler.getOfflinePermission(player, node, context);
                if (value != null) {
                    return value;
                }
            }
            return node.getDefaultResolver().resolve(null, player, context);
        }

        private static class Builder implements IPermissionHandlerFactory {
            private final List<Entry> entries = new ArrayList<>();

            public void add(int priority, PermissionNodeHandler handler) {
                entries.add(new Entry(priority, handler));
            }

            @Override
            public IPermissionHandler create(Collection<PermissionNode<?>> permissions) {
                List<PermissionNodeHandler> handlers = entries.stream()
                        .sorted(Comparator.comparingInt(Entry::priority))
                        .map(Entry::handler)
                        .toList();
                return new NeoHandler(Set.copyOf(permissions), handlers);
            }

            private record Entry(int priority, PermissionNodeHandler handler) {
            }
        }
    }
}
