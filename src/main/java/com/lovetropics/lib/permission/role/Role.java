package com.lovetropics.lib.permission.role;

public interface Role extends Comparable<Role> {
    String EVERYONE = "everyone";

    String id();

    int index();

    RoleOverrideReader overrides();

    @Override
    default int compareTo(Role role) {
        int compareIndex = Integer.compare(role.index(), index());
        return compareIndex != 0 ? compareIndex : id().compareTo(role.id());
    }
}
