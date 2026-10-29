package com.tygabeast.arssourcequarry.client;

import com.hollingsworth.arsnouveau.client.particle.ColorParticleTypeData;
import com.hollingsworth.arsnouveau.client.particle.ParticleColor;
import com.tygabeast.arssourcequarry.ArsSourceQuarry;
import com.tygabeast.arssourcequarry.block.ClientQuarries;
import com.tygabeast.arssourcequarry.block.MarkerBlockEntity;
import com.tygabeast.arssourcequarry.block.SourceQuarryBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws quarry areas with Ars glow particles, client side only, every 10 ticks:
 *  - every nearby quarry whose outline toggle is on;
 *  - while the quarry screen is open, that quarry's area as currently set (unapplied Up/Down included);
 *  - while holding a Ley Marker or Dominion Wand: all nearby quarry areas, the markers themselves
 *    (cyan when paired, red when still waiting for a partner) and each pair's boundary rectangle.
 * Quarries come from a client-side set (no chunk search); markers are read from nearby chunks'
 * block-entity maps only while holding a marker or wand. Particles per pass are capped.
 */
@EventBusSubscriber(modid = ArsSourceQuarry.MODID, value = Dist.CLIENT)
public final class QuarryOutline {
    private static final int RANGE = 64, MARKER_RANGE = 48, MAX_PARTICLES = 900;
    private static final ResourceLocation WAND = ResourceLocation.fromNamespaceAndPath("ars_nouveau", "dominion_wand");
    private static ColorParticleTypeData edge, preview, markerGlow, unpairedGlow;
    private static Item wandItem;
    private static int budget;

    private QuarryOutline() {}

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) ClientQuarries.clear();
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null || mc.isPaused()) return;
        if (level.getGameTime() % 10 != 0) return;
        QuarryHum.update(mc);
        ensureParticles();
        budget = MAX_PARTICLES;

        boolean tool = holdsTool(player.getMainHandItem().getItem()) || holdsTool(player.getOffhandItem().getItem());
        BlockPos p = player.blockPosition();

        // Screen open: preview the area as currently set.
        if (mc.screen instanceof SourceQuarryScreen screen) {
            int[] box = screen.previewBox();
            if (box != null) drawBox(level, box, preview);
        }

        for (SourceQuarryBlockEntity q : ClientQuarries.all()) {
            if (q.getLevel() != level || !q.clientLinked()) continue;
            if (!q.clientShowsOutline() && !tool) continue;
            if (q.getBlockPos().distManhattan(p) > RANGE * 3) continue;
            drawBox(level, q.clientBox(), edge);
        }

        if (!tool) return;
        List<MarkerBlockEntity> markers = new ArrayList<>(4);
        for (int chx = (p.getX() - MARKER_RANGE) >> 4; chx <= (p.getX() + MARKER_RANGE) >> 4; chx++) {
            for (int chz = (p.getZ() - MARKER_RANGE) >> 4; chz <= (p.getZ() + MARKER_RANGE) >> 4; chz++) {
                LevelChunk chunk = level.getChunkSource().getChunk(chx, chz, false);
                if (chunk == null) continue;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (be instanceof MarkerBlockEntity m) markers.add(m);
                }
            }
        }
        for (MarkerBlockEntity m : markers) {
            BlockPos a = m.getBlockPos();
            BlockPos b = m.getPartner();
            // Paired markers: the boundary rectangle, drawn once per pair. Unpaired: a different glow.
            level.addParticle(b != null ? markerGlow : unpairedGlow, a.getX() + 0.5, a.getY() + 1.0, a.getZ() + 0.5, 0, 0.02, 0);
            if (b != null && a.asLong() < b.asLong()) {
                int y = Math.min(a.getY(), b.getY());
                drawBox(level, new int[]{Math.min(a.getX(), b.getX()), y, Math.min(a.getZ(), b.getZ()),
                        Math.max(a.getX(), b.getX()), y, Math.max(a.getZ(), b.getZ())}, preview);
            }
        }
    }

    private static boolean holdsTool(Item item) {
        if (wandItem == null) wandItem = BuiltInRegistries.ITEM.get(WAND);
        return item == ArsSourceQuarry.LEY_MARKER_ITEM.get() || item == wandItem;
    }

    private static void ensureParticles() {
        if (edge != null) return;
        edge = new ColorParticleTypeData(new ParticleColor(190, 70, 255), true, 0.2f, 0.8f, 14);
        preview = new ColorParticleTypeData(new ParticleColor(255, 200, 90), true, 0.2f, 0.9f, 14);
        markerGlow = new ColorParticleTypeData(new ParticleColor(80, 220, 255), true, 0.35f, 0.9f, 14);
        unpairedGlow = new ColorParticleTypeData(new ParticleColor(255, 90, 90), true, 0.35f, 0.9f, 14);
    }

    /** box = minX, minY, minZ, maxX, maxY, maxZ (block coordinates, inclusive). */
    private static void drawBox(ClientLevel level, int[] box, ColorParticleTypeData type) {
        if (budget <= 0) return;
        double x0 = box[0], y0 = box[1], z0 = box[2], x1 = box[3] + 1, y1 = box[4] + 1, z1 = box[5] + 1;
        double total = 4 * ((x1 - x0) + (z1 - z0) + (y1 - y0));
        double step = Math.max(1.0, total / Math.max(1, Math.min(budget, MAX_PARTICLES / 2)));
        for (double y : new double[]{y0, y1}) {
            line(level, type, x0, y, z0, x1, y, z0, step);
            line(level, type, x0, y, z1, x1, y, z1, step);
            line(level, type, x0, y, z0, x0, y, z1, step);
            line(level, type, x1, y, z0, x1, y, z1, step);
        }
        line(level, type, x0, y0, z0, x0, y1, z0, step);
        line(level, type, x1, y0, z0, x1, y1, z0, step);
        line(level, type, x0, y0, z1, x0, y1, z1, step);
        line(level, type, x1, y0, z1, x1, y1, z1, step);
    }

    private static void line(ClientLevel level, ColorParticleTypeData type, double ax, double ay, double az,
                             double bx, double by, double bz, double step) {
        double len = Math.max(Math.abs(bx - ax), Math.max(Math.abs(by - ay), Math.abs(bz - az)));
        int n = Math.max(1, (int) (len / step));
        for (int i = 0; i <= n && budget > 0; i++, budget--) {
            double t = (double) i / n;
            level.addParticle(type, ax + (bx - ax) * t, ay + (by - ay) * t, az + (bz - az) * t, 0, 0, 0);
        }
    }
}
