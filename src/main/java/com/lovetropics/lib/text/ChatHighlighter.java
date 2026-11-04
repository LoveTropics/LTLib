package com.lovetropics.lib.text;

import net.minecraft.network.chat.Component;

public final class ChatHighlighter {
    private static Access access = Access.NONE;

    private ChatHighlighter() {
    }

    public static Component highlight(Component message, int leftColor, int rightColor) {
        return access.highlight(message, leftColor, rightColor);
    }

    public static void bindAccess(Access access) {
        ChatHighlighter.access = access;
    }

    @FunctionalInterface
    public interface Access {
        Access NONE = (message, leftColor, rightColor) -> message;

        Component highlight(Component message, int leftColor, int rightColor);
    }
}
