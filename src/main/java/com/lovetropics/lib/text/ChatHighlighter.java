package com.lovetropics.lib.text;

import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

public final class ChatHighlighter {
    private static Access access = Access.NONE;

    private ChatHighlighter() {
    }

    public static Component highlight(Component message, int leftColor, int rightColor) {
        return access.highlight(message, leftColor, rightColor);
    }

    public static @Nullable Highlight extract(Component message) {
        return access.extract(message);
    }

    public static void bindAccess(Access access) {
        ChatHighlighter.access = access;
    }

    public interface Access {
        Access NONE = new Access() {
            @Override
            public Component highlight(Component message, int leftColor, int rightColor) {
                return message;
            }

            @Override
            public @Nullable Highlight extract(Component message) {
                return null;
            }
        };

        Component highlight(Component message, int leftColor, int rightColor);

        @Nullable Highlight extract(Component message);
    }

    public record Highlight(int leftColor, int rightColor) {
    }
}
