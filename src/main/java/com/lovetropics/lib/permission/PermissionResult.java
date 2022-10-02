package com.lovetropics.lib.permission;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.ChatFormatting;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Stream;

public enum PermissionResult implements StringRepresentable {
    PASS("pass", ChatFormatting.AQUA),
    ALLOW("allow", ChatFormatting.GREEN),
    DENY("deny", ChatFormatting.RED);

    public static final Codec<PermissionResult> CODEC = StringRepresentable.fromEnum(PermissionResult::values, PermissionResult::byKey);

    private static final String[] KEYS = { "pass", "allow", "deny" };

    private final String key;
    private final Component name;

    PermissionResult(String key, ChatFormatting color) {
        this.key = key;
        this.name = new TextComponent(key).withStyle(color);
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

    public static PermissionResult byKey(String key) {
        return switch (key.toLowerCase(Locale.ROOT)) {
            case "allow", "yes", "true" -> PermissionResult.ALLOW;
            case "deny", "no", "false" -> PermissionResult.DENY;
            default -> PermissionResult.PASS;
        };
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
