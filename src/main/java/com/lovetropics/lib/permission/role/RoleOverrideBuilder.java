package com.lovetropics.lib.permission.role;

import org.jspecify.annotations.Nullable;
import java.util.List;

public interface RoleOverrideBuilder<T> {
    static <T> RoleOverrideBuilder<T> first() {
        return overrides -> overrides.isEmpty() ? null : overrides.get(0);
    }

    @Nullable T apply(List<T> overrides);
}
