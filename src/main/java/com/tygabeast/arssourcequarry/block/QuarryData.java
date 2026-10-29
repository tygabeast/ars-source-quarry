package com.tygabeast.arssourcequarry.block;

import net.minecraft.world.inventory.ContainerData;

/**
 * Indices of the values the quarry screen shows, and the button ids it sends.
 * Vanilla syncs container data as 16-bit shorts, so every int is sent as two halves.
 */
public final class QuarryData {
    public static final int STATUS = 0, PROGRESS = 1, BUFFER = 2, CAPACITY = 3, COST = 4, LAYER = 5,
            AX = 6, AY = 7, AZ = 8, BX = 9, BY = 10, BZ = 11, LINKED = 12, UP = 13, DOWN = 14, APPLY = 15,
            REDSTONE = 16, OUTLINE = 17, ESTIMATE = 18, BASE_Y = 19, MINED = 20, LOCKED = 21, CAN_LOCK = 22,
            VOID_WHITELIST = 23, VOID_TAGS = 24, SKIPPED = 25, CHUNK_LOADED = 26;
    public static final int INTS = 27, SLOTS = INTS * 2;

    /** What the Apply button would do. */
    public static final int APPLY_NONE = 0, APPLY_EXTEND = 1, APPLY_RESTART = 2;

    public static final int B_UP_MINUS = 0, B_UP_PLUS = 1, B_UP_MINUS10 = 2, B_UP_PLUS10 = 3,
            B_DOWN_MINUS = 4, B_DOWN_PLUS = 5, B_DOWN_MINUS10 = 6, B_DOWN_PLUS10 = 7,
            B_BEDROCK = 8, B_APPLY = 9, B_REDSTONE = 10, B_OUTLINE = 11, B_REDSTONE_BACK = 12,
            B_LOCK = 13, B_VOID_MODE = 14, B_VOID_TAGS = 15;

    private QuarryData() {}

    /** Reads a full int back from its two synced halves. */
    public static int get(ContainerData d, int index) {
        return (d.get(index * 2) & 0xFFFF) | (d.get(index * 2 + 1) << 16);
    }

    /** Server side: wraps an int source as split container data. */
    public static ContainerData split(java.util.function.IntUnaryOperator values) {
        return new ContainerData() {
            @Override
            public int get(int i) {
                int v = values.applyAsInt(i / 2);
                return (i & 1) == 0 ? v & 0xFFFF : v >>> 16;
            }

            @Override
            public void set(int i, int value) {
                // read-only from the client's point of view; buttons go through clickMenuButton
            }

            @Override
            public int getCount() {
                return SLOTS;
            }
        };
    }
}
