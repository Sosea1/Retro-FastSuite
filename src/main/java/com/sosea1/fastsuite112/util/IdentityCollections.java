package com.sosea1.fastsuite112.util;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

public final class IdentityCollections {
    private IdentityCollections() {}

    public static <T> Set<T> newIdentitySet() {
        return Collections.newSetFromMap(new IdentityHashMap<T, Boolean>());
    }
}
