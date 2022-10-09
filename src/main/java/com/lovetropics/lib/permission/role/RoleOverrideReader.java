package com.lovetropics.lib.permission.role;

import com.lovetropics.lib.permission.PermissionResult;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

public interface RoleOverrideReader {
    RoleOverrideReader EMPTY = new RoleOverrideReader() {
        @Override
        @Nullable
        public <T> T getOrNull(RoleOverrideType<T> type) {
            return null;
        }

        @Override
        public <T> PermissionResult test(RoleOverrideType<T> type, Function<T, PermissionResult> function) {
            return PermissionResult.PASS;
        }

        @Override
        public boolean test(RoleOverrideType<Boolean> type) {
            return false;
        }

        @Override
        public Set<RoleOverrideType<?>> typeSet() {
            return Collections.emptySet();
        }
    };

    @Nullable
    <T> T getOrNull(RoleOverrideType<T> type);

    default <T> T get(RoleOverrideType<T> type, T defaultValue) {
        return Objects.requireNonNullElse(getOrNull(type), defaultValue);
    }

    default <T> PermissionResult test(RoleOverrideType<T> type, Function<T, PermissionResult> function) {
        T override = getOrNull(type);
        if (override == null) {
            return PermissionResult.PASS;
        }
        return function.apply(override);
    }

    default boolean test(RoleOverrideType<Boolean> type) {
        return get(type, Boolean.FALSE);
    }

    Set<RoleOverrideType<?>> typeSet();
}
