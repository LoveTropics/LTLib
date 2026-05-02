package com.lovetropics.lib.permission.role;

import java.util.Collections;
import java.util.Iterator;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public interface RoleReader extends Iterable<Role> {
    RoleReader EMPTY = new RoleReader() {
        @Override
        public Iterator<Role> iterator() {
            return Collections.emptyIterator();
        }

        @Override
        public boolean has(Role role) {
            return false;
        }

        @Override
        public RoleOverrideReader overrides() {
            return RoleOverrideReader.EMPTY;
        }
    };

    default Stream<Role> stream() {
        return StreamSupport.stream(this.spliterator(), false);
    }

    boolean has(Role role);

    RoleOverrideReader overrides();
}
