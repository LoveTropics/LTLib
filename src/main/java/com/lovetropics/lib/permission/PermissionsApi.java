package com.lovetropics.lib.permission;

import com.lovetropics.lib.permission.role.RoleLookup;
import com.lovetropics.lib.permission.role.RoleModifier;
import com.lovetropics.lib.permission.role.RoleProvider;

public final class PermissionsApi {
    private static RoleProvider provider = RoleProvider.EMPTY;
    private static RoleLookup lookup = RoleLookup.EMPTY;
    private static RoleModifier modifier = RoleModifier.EMPTY;

    public static void setRoleProvider(RoleProvider provider) {
        PermissionsApi.provider = provider;
    }

    public static void setRoleLookup(RoleLookup lookup) {
        PermissionsApi.lookup = lookup;
    }

    public static void setRoleModifier(RoleModifier modifier) {
        PermissionsApi.modifier = modifier;
    }

    public static RoleLookup lookup() {
        return PermissionsApi.lookup;
    }

    public static RoleProvider provider() {
        return PermissionsApi.provider;
    }

    public static RoleModifier modifier() {
        return PermissionsApi.modifier;
    }
}
