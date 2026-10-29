package com.tygabeast.arssourcequarry.client;

import com.tygabeast.arssourcequarry.QuarryClientConfig;
import com.tygabeast.arssourcequarry.block.ClientQuarries;
import com.tygabeast.arssourcequarry.block.SourceQuarryBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * A quiet looping hum near a quarry while it mines. Client only. The server only tells clients when
 * mining starts or stops (at most every 2 seconds), so this costs no network traffic while running.
 */
public class QuarryHum extends AbstractTickableSoundInstance {
    private static final double RANGE_SQR = 24 * 24;
    private static final Map<SourceQuarryBlockEntity, QuarryHum> PLAYING = new WeakHashMap<>();
    private final SourceQuarryBlockEntity quarry;

    private QuarryHum(SourceQuarryBlockEntity quarry) {
        super(SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
        this.quarry = quarry;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.3f;
        this.pitch = 0.7f;
        this.x = quarry.getBlockPos().getX() + 0.5;
        this.y = quarry.getBlockPos().getY() + 0.5;
        this.z = quarry.getBlockPos().getZ() + 0.5;
    }

    @Override
    public void tick() {
        LocalPlayer p = Minecraft.getInstance().player;
        if (quarry.isRemoved() || !quarry.clientRunning() || !QuarryClientConfig.HUM.get() || p == null
                || p.distanceToSqr(x, y, z) > RANGE_SQR * 1.5) {
            stop();
        }
    }

    /** Called every 10 ticks from the client tick: starts hums for running quarries in range. */
    static void update(Minecraft mc) {
        if (!QuarryClientConfig.HUM.get() || mc.player == null) return;
        PLAYING.values().removeIf(QuarryHum::isStopped);
        for (SourceQuarryBlockEntity q : ClientQuarries.all()) {
            if (!q.clientRunning() || PLAYING.containsKey(q)) continue;
            if (mc.player.distanceToSqr(q.getBlockPos().getCenter()) > RANGE_SQR) continue;
            QuarryHum hum = new QuarryHum(q);
            PLAYING.put(q, hum);
            mc.getSoundManager().play(hum);
        }
    }
}
