package com.lovetropics.lib.permission.role;

import com.lovetropics.lib.permission.PermissionResult;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

public interface RoleOverrideReader {
    RoleOverrideReader EMPTY = new RoleOverrideReader() {
        @Override
        @Nullable
        public <T> Collection<T> getOrNull(RoleOverrideType<T> type) {
            return null;
        }

        @Override
        public <T> PermissionResult test(RoleOverrideType<T> type, Function<T, PermissionResult> function) {
            return PermissionResult.PASS;
        }

        @Override
        @Nullable
        public <T> T select(RoleOverrideType<T> type) {
            return null;
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
    <T> Collection<T> getOrNull(RoleOverrideType<T> type);

    @Nonnull
    default <T> Collection<T> get(RoleOverrideType<T> type) {
        Collection<T> overrides = this.getOrNull(type);
        return overrides != null ? overrides : Collections.emptyList();
    }

    default <T> Stream<T> streamOf(RoleOverrideType<T> type) {
        return this.get(type).stream();
    }

    default <T> PermissionResult test(RoleOverrideType<T> type, Function<T, PermissionResult> function) {
        Collection<T> overrides = this.getOrNull(type);
        if (overrides == null) {
            return PermissionResult.PASS;
        }

        for (T override : overrides) {
            PermissionResult result = function.apply(override);
            if (result.isTerminator()) {
                return result;
            }
        }

        return PermissionResult.PASS;
    }

    @Nullable
    default <T> T select(RoleOverrideType<T> type) {
        Collection<T> overrides = this.getOrNull(type);
        if (overrides != null) {
            for (T override : overrides) {
                return override;
            }
        }
        return null;
    }

    default boolean test(RoleOverrideType<Boolean> type) {
        Boolean result = this.select(type);
        return result != null ? result : false;
    }

    Set<RoleOverrideType<?>> typeSet();
}
