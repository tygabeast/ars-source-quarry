package com.tygabeast.arssourcequarry;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server (per-world) settings. Read on the server only. */
public final class QuarryConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.IntValue LINK_RANGE;
    public static final ModConfigSpec.IntValue MAX_SIZE;
    public static final ModConfigSpec.IntValue TICKS_PER_BLOCK;
    public static final ModConfigSpec.IntValue SCAN_PER_TICK;
    public static final ModConfigSpec.IntValue SOURCE_RANGE;
    public static final ModConfigSpec.IntValue BUFFER_CAPACITY;
    public static final ModConfigSpec.IntValue BASE_COST;
    public static final ModConfigSpec.DoubleValue COST_PER_HARDNESS;
    public static final ModConfigSpec.IntValue PUMP_COST;
    public static final ModConfigSpec.IntValue EVAPORATE_COST;
    public static final ModConfigSpec.DoubleValue AMPLIFY_COST;
    public static final ModConfigSpec.IntValue EXTRACT_COST;
    public static final ModConfigSpec.IntValue FORTUNE_COST;
    public static final ModConfigSpec.IntValue SMELT_COST;
    public static final ModConfigSpec.IntValue CRUSH_COST;
    public static final ModConfigSpec.DoubleValue MAX_HARDNESS;
    public static final ModConfigSpec.BooleanValue MINE_BLOCK_ENTITIES;
    public static final ModConfigSpec.BooleanValue PAUSE_FOR_PLAYERS;
    public static final ModConfigSpec.BooleanValue COLLECT_MARKERS;
    public static final ModConfigSpec.BooleanValue EFFECTS;
    public static final ModConfigSpec.BooleanValue CHUNK_LOADING;
    public static final ModConfigSpec.IntValue CHUNK_LOAD_COST;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("linking");
        LINK_RANGE = b.comment("How far (in blocks, each axis) the quarry looks for the nearest Ley Marker.",
                        "The second marker is then searched around that marker, out to maxSize.")
                .defineInRange("linkRange", 32, 4, 64);
        MAX_SIZE = b.comment("Largest allowed width or length of the marked area, in blocks.")
                .defineInRange("maxSize", 64, 1, 256);
        b.pop();

        b.push("speed");
        TICKS_PER_BLOCK = b.comment("Ticks between mining actions (20 ticks = 1 second).",
                        "Each Amplify glyph mines one extra block per action.")
                .defineInRange("ticksPerBlock", 2, 1, 200);
        SCAN_PER_TICK = b.comment("How many empty or skipped positions the quarry may pass over in one tick.",
                        "Higher clears air faster; each position is one cheap block lookup.")
                .defineInRange("scanPerTick", 256, 1, 4096);
        b.pop();

        b.push("source");
        SOURCE_RANGE = b.comment("Range the quarry pulls Source from (jars, and anything Ars Nouveau treats as a Source provider).")
                .defineInRange("sourceRange", 6, 1, 16);
        BUFFER_CAPACITY = b.comment("Source the quarry holds internally. It refills from jars at most once a second.")
                .defineInRange("bufferCapacity", 1000, 50, 100000);
        BASE_COST = b.comment("Source spent per mined block, before hardness.")
                .defineInRange("baseCost", 2, 0, 10000);
        COST_PER_HARDNESS = b.comment("Extra Source per point of block hardness (stone is 1.5, obsidian 50).")
                .defineInRange("costPerHardness", 1.0, 0.0, 1000.0);
        PUMP_COST = b.comment("Source spent to move one fluid source block into a neighbouring tank.")
                .defineInRange("pumpCost", 2, 0, 10000);
        EVAPORATE_COST = b.comment("Source spent to evaporate one fluid source block when no tank has room.")
                .defineInRange("evaporateCost", 25, 0, 10000);
        b.pop();

        b.push("glyphs");
        AMPLIFY_COST = b.comment("Each Amplify glyph multiplies the block cost by (1 + this).")
                .defineInRange("amplifyCostPerLevel", 0.5, 0.0, 100.0);
        EXTRACT_COST = b.comment("Extra Source per block with Extract (Silk Touch).")
                .defineInRange("extractCost", 3, 0, 10000);
        FORTUNE_COST = b.comment("Extra Source per block for each Fortune glyph.")
                .defineInRange("fortuneCostPerLevel", 2, 0, 10000);
        SMELT_COST = b.comment("Extra Source per block with Smelt.")
                .defineInRange("smeltCost", 1, 0, 10000);
        CRUSH_COST = b.comment("Extra Source per block with Crush.")
                .defineInRange("crushCost", 1, 0, 10000);
        b.pop();

        b.push("mining");
        MAX_HARDNESS = b.comment("Blocks harder than this are skipped. Unbreakable blocks are always skipped.")
                .defineInRange("maxHardness", 50.0, 0.0, 10000.0);
        MINE_BLOCK_ENTITIES = b.comment("Whether blocks with block entities (chests, jars, machines, spawners) are mined.",
                        "Off by default so the quarry never breaks your own machines or empties containers.")
                .define("mineBlockEntities", false);
        PAUSE_FOR_PLAYERS = b.comment("Pause while a player (not in spectator mode) is inside the area.")
                .define("pauseForPlayers", true);
        COLLECT_MARKERS = b.comment("When finished, put the two Ley Markers into the output container if there is room.",
                        "Off by default: with it on, the markers are gone if you later dig the same area deeper.")
                .define("collectMarkersWhenDone", false);
        EFFECTS = b.comment("Source particles at the mined block and Ars Source trails to the quarry.")
                .define("effects", true);
        b.pop();

        b.push("chunkLoading");
        CHUNK_LOADING = b.comment("Let quarries keep their own chunk and the chunk they are mining loaded while they work,",
                        "so they keep going with nobody nearby. Costs server performance, so it is off by default.",
                        "A quarry only holds chunks while it is actually mining and can pay the upkeep.")
                .define("enabled", false);
        CHUNK_LOAD_COST = b.comment("Source per second a quarry pays while it keeps chunks loaded.")
                .defineInRange("upkeepPerSecond", 5, 0, 10000);
        b.pop();
        SPEC = b.build();
    }

    private QuarryConfig() {}
}
