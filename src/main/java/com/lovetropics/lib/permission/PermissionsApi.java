package com.lovetropics.lib.permission;

import com.lovetropics.lib.permission.role.RoleLookup;
import com.lovetropics.lib.permission.role.RoleProvider;

public final class PermissionsApi {
    private static RoleProvider provider = RoleProvider.EMPTY;
    private static RoleLookup lookup = RoleLookup.EMPTY;

    public static void setRoleProvider(RoleProvider provider) {
        PermissionsApi.provider = provider;
    }

    public static void setRoleLookup(RoleLookup lookup) {
        PermissionsApi.lookup = lookup;
    }

    public static RoleLookup lookup() {
        return PermissionsApi.lookup;
    }

    public static RoleProvider provider() {
        return PermissionsApi.provider;
    }
}
