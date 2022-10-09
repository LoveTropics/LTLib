package com.lovetropics.lib.permission.role;

public interface RoleOverrideBuilder<T> {
    static <T> RoleOverrideBuilder<T> first() {
        return overrides -> overrides[0];
    }

    T apply(T[] overrides);
}
