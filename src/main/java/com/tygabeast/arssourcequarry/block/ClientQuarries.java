package com.tygabeast.arssourcequarry.block;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Quarries loaded on the client, so the outline renderer never has to search chunks for them.
 * Only ever filled on the client side; weak keys so an unloaded level cannot leak.
 */
public final class ClientQuarries {
    private static final Set<SourceQuarryBlockEntity> LOADED = Collections.newSetFromMap(new WeakHashMap<>());

    private ClientQuarries() {}

    static void add(SourceQuarryBlockEntity be) {
        LOADED.add(be);
    }

    static void remove(SourceQuarryBlockEntity be) {
        LOADED.remove(be);
    }

    public static Set<SourceQuarryBlockEntity> all() {
        return LOADED;
    }

    public static void clear() {
        LOADED.clear();
    }
}
