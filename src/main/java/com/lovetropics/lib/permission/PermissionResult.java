package com.lovetropics.lib.permission;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

import org.jspecify.annotations.Nullable;
import java.util.Arrays;
import java.util.stream.Stream;

public enum PermissionResult implements StringRepresentable {
    PASS("pass", ChatFormatting.AQUA),
    ALLOW("allow", ChatFormatting.GREEN),
    DENY("deny", ChatFormatting.RED);

    public static final EnumCodec<PermissionResult> CODEC = StringRepresentable.fromEnum(PermissionResult::values);

    private static final String[] KEYS = Arrays.stream(values()).map(PermissionResult::getSerializedName).toArray(String[]::new);

    private final String key;
    private final Component name;

    PermissionResult(String key, ChatFormatting color) {
        this.key = key;
        this.name = Component.literal(key).withStyle(color);
    }

    public boolean isTerminator() {
        return this != PASS;
    }

    public boolean isAllowed() {
        return this == ALLOW;
    }

    public boolean isDenied() {
        return this == DENY;
    }

    public static @Nullable PermissionResult byKey(String key) {
        return CODEC.byName(key);
    }

    @Override
    public String getSerializedName() {
        return this.key;
    }

    public Component getName() {
        return this.name;
    }

    public static Stream<String> keysStream() {
        return Arrays.stream(KEYS);
    }
}
