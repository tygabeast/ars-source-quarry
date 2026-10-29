package com.tygabeast.arssourcequarry;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client-only settings. */
public final class QuarryClientConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue HUM;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        HUM = b.comment("Play a quiet hum near quarries while they are mining.").define("quarryHum", true);
        SPEC = b.build();
    }

    private QuarryClientConfig() {}
}
