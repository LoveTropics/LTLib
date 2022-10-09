package com.lovetropics.lib.permission.role;

import com.lovetropics.lib.codec.CodecRegistry;
import com.mojang.serialization.Codec;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.List;

public final class RoleOverrideType<T> {
    public static final CodecRegistry<String, RoleOverrideType<?>> REGISTRY = CodecRegistry.stringKeys();

    private final String id;
    private final Codec<T> codec;
    private RoleOverrideBuilder<T> builder = RoleOverrideBuilder.first();
    private RoleListener initializeListener;
    private RoleListener changeListener;

    private RoleOverrideType(String id, Codec<T> codec) {
        this.id = id;
        this.codec = codec;
    }

    public static <T> RoleOverrideType<T> register(String id, Codec<T> codec) {
        RoleOverrideType<T> type = new RoleOverrideType<>(id, codec);
        REGISTRY.register(id, type);
        return type;
    }

    public RoleOverrideType<T> withBuilder(RoleOverrideBuilder<T> builder) {
        this.builder = builder;
        return this;
    }

    public RoleOverrideType<T> withInitializeListener(RoleListener listener) {
        this.initializeListener = listener;
        return this;
    }

    public RoleOverrideType<T> withChangeListener(RoleListener listener) {
        this.changeListener = listener;
        return this;
    }

    public String getId() {
        return this.id;
    }

    public Codec<T> getCodec() {
        return this.codec;
    }

    public T build(List<T> overrides) {
        return builder.apply(overrides);
    }

    public void notifyInitialize(ServerPlayer player) {
        if (this.initializeListener != null) {
            this.initializeListener.accept(player);
        }
    }

    public void notifyChange(ServerPlayer player) {
        if (this.changeListener != null) {
            this.changeListener.accept(player);
        }
    }

    @Nullable
    public static RoleOverrideType<?> byId(String id) {
        return REGISTRY.get(id);
    }

    @Override
    public String toString() {
        return "RoleOverrideType(" + this.id + ")";
    }
}
