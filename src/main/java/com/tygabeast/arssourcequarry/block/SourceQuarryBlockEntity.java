package com.tygabeast.arssourcequarry.block;

import com.hollingsworth.arsnouveau.api.source.ISourceCap;
import com.hollingsworth.arsnouveau.api.source.ISourceTile;
import com.hollingsworth.arsnouveau.api.source.ISpecialSourceProvider;
import com.hollingsworth.arsnouveau.api.util.SourceUtil;
import com.hollingsworth.arsnouveau.client.particle.ColorParticleTypeData;
import com.hollingsworth.arsnouveau.client.particle.ParticleColor;
import com.hollingsworth.arsnouveau.common.block.tile.CreativeSourceJarTile;
import com.hollingsworth.arsnouveau.common.crafting.recipes.CrushRecipe;
import com.hollingsworth.arsnouveau.common.entity.EntityFollowProjectile;
import com.hollingsworth.arsnouveau.setup.registry.RecipeRegistry;
import com.mojang.authlib.GameProfile;
import com.tygabeast.arssourcequarry.ArsSourceQuarry;
import com.tygabeast.arssourcequarry.QuarryConfig;
import com.tygabeast.arssourcequarry.menu.SourceQuarryMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Mines the linked area from the top layer down.
 *
 * Cost notes (server, per quarry):
 *  - Work is driven by a saved cursor, so nothing is ever rescanned. Air and skipped blocks cost one
 *    getBlockState each, capped by scanPerTick.
 *  - Source is pulled into an internal buffer at most once a second, so Ars Nouveau's provider search
 *    runs rarely instead of once per block.
 *  - Output containers are reached through BlockCapabilityCache (NeoForge invalidates it for us).
 *  - Glyph effects, the enchanted tool and the void filter are rebuilt only when their slots change.
 *    Smelt and Crush results are cached per item until recipes reload.
 *  - The player-in-area check walks the level's player list once a second; no entity searches.
 *  - Clients receive the area only when it or the outline setting changes (for the outline).
 */
public class SourceQuarryBlockEntity extends BlockEntity implements MenuProvider {
    private static final GameProfile PROFILE =
            new GameProfile(UUID.fromString("5f1a7c2e-3b9d-4e61-9a0f-a5c0e7d2b4f1"), "[Ars Source Quarry]");
    public static final int DEFAULT_DOWN = 8;

    public enum Status {
        NOT_LINKED, RUNNING, NO_SOURCE, NO_OUTPUT, OUTPUT_FULL, WAITING_REDSTONE, DISABLED, PLAYER_IN_AREA,
        WAITING_CHUNK, FINISHING, DONE;
        public String key() { return "status.ars_source_quarry." + name().toLowerCase(); }
    }

    /** ALWAYS runs; HIGH needs a signal; LOW needs no signal; OFF never runs. */
    public enum RedstoneMode {
        ALWAYS, HIGH, LOW, OFF;
        public String key() { return "redstone.ars_source_quarry." + name().toLowerCase(); }
    }

    private enum Outcome { SKIPPED, WORKED, WAIT }

    // ---- saved state ----
    private boolean linked, done;
    private int minX, minY, minZ, maxX, maxY, maxZ;
    private int cx, cy, cz;
    private int buffer;
    private long mined, spent;
    private @Nullable BlockPos markerA, markerB;
    private int up, down = DEFAULT_DOWN, appliedUp, appliedDown;
    private RedstoneMode redstoneMode = RedstoneMode.ALWAYS;
    private boolean outline;
    private @Nullable UUID owner;
    private String ownerName = "";
    private boolean locked = true;
    private boolean voidWhitelist, voidTags;
    private long skipped;
    private final List<ItemStack> pending = new ArrayList<>();
    private final Map<Long, Integer> dirtyColumns = new LinkedHashMap<>();
    private final ArrayDeque<Long> recheck = new ArrayDeque<>();
    private final QuarryGlyphs.Handler glyphs = new QuarryGlyphs.Handler(this::glyphsChanged);
    private final ItemStackHandler voidFilter = new ItemStackHandler(6) {
        @Override
        protected void onContentsChanged(int slot) {
            voidItems = null;
            voidTagSet = null;
            setChanged();
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }
    };

    // ---- runtime only ----
    private boolean powered, linkTried, playerInArea;
    private Status status = Status.NOT_LINKED;
    private @Nullable Component linkError;
    private int cooldown, flushTimer, finishTimer, comparator;
    private long nextRefill, nextBeam, nextPlayerCheck;
    private @Nullable List<BlockCapabilityCache<IItemHandler, Direction>> itemOut;
    private @Nullable List<BlockCapabilityCache<IFluidHandler, Direction>> fluidOut;
    private final BlockPos.MutableBlockPos cursorPos = new BlockPos.MutableBlockPos();
    private @Nullable ItemStack tool;
    private @Nullable Set<Item> voidItems;
    private @Nullable Set<TagKey<Item>> voidTagSet;
    private boolean forcedQuarryChunk;
    private long forcedAreaChunk = Long.MIN_VALUE;
    private long nextUpkeep, nextRunningSync;
    private boolean syncedRunning;
    private final SourceCap sourceCap = new SourceCap();
    private int amplify = -1, extraCost;
    private double costMultiplier = 1;
    private boolean smelt, crush;

    // ---- client only (outline) ----
    private boolean clientLinked, clientOutline, clientRunning;
    private int[] clientBox = new int[6];

    private static @Nullable ColorParticleTypeData glow;
    private static @Nullable RecipeManager cachedRecipes;
    private static final Map<Item, ItemStack> SMELT_CACHE = new HashMap<>();
    private static final Map<Item, Optional<CrushRecipe>> CRUSH_CACHE = new HashMap<>();

    public SourceQuarryBlockEntity(BlockPos pos, BlockState state) {
        super(ArsSourceQuarry.QUARRY_BE.get(), pos, state);
    }

    // =====================================================================================
    // Linking and the area
    // =====================================================================================

    /** Reads the markers and starts over from the top layer with the current Up/Down values. */
    public void link(@Nullable ServerPlayer feedback) {
        if (!(level instanceof ServerLevel sl)) return;
        linkTried = true;
        linked = false;
        done = false;
        recheck.clear();
        dirtyColumns.clear();
        linkError = null;

        BlockPos[] pair = findMarkers(sl, feedback);
        if (pair == null) {
            syncToClients();
            updateComparator();
            return;
        }
        markerA = pair[0];
        markerB = pair[1];
        int base = Math.min(markerA.getY(), markerB.getY());
        up = Math.max(0, Math.min(up, sl.getMaxBuildHeight() - 1 - base));
        down = Math.max(0, Math.min(down, base - sl.getMinBuildHeight()));
        appliedUp = up;
        appliedDown = down;
        // Only the inside of the boundary is mined: the marker rows and columns stay untouched at every depth,
        // so the markers keep the ground under them.
        minX = Math.min(markerA.getX(), markerB.getX()) + 1;
        maxX = Math.max(markerA.getX(), markerB.getX()) - 1;
        minZ = Math.min(markerA.getZ(), markerB.getZ()) + 1;
        maxZ = Math.max(markerA.getZ(), markerB.getZ()) - 1;
        minY = base - down;
        maxY = base + up;
        cx = minX;
        cy = maxY;
        cz = minZ;
        mined = 0;
        spent = 0;
        skipped = 0;
        linked = true;
        status = Status.RUNNING;
        setChanged();
        syncToClients();
        updateComparator();
        if (QuarryConfig.CHUNK_LOADING.get()) manageChunks(sl);   // claim the chunk right away, not a second later
        if (feedback != null) {
            feedback.sendSystemMessage(Component.translatable("message.ars_source_quarry.linked",
                    maxX - minX + 1, maxZ - minZ + 1, maxY - minY + 1, minY, maxY).withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE));
        }
    }

    /**
     * Uses the nearest paired marker within linkRange and its partner. Markers that have no partner yet
     * (placed before pairing existed) are paired here with the nearest unpaired marker within maxSize.
     * Reads chunk block-entity maps instead of scanning blocks; only runs when linking.
     */
    private @Nullable BlockPos[] findMarkers(ServerLevel sl, @Nullable ServerPlayer feedback) {
        int r = QuarryConfig.LINK_RANGE.get();
        int max = QuarryConfig.MAX_SIZE.get();
        List<MarkerBlockEntity> near = markersAround(sl, worldPosition, r);
        if (near.isEmpty()) {
            fail(feedback, Component.translatable("message.ars_source_quarry.no_marker", r));
            return null;
        }
        // The nearest marker decides which area this quarry works on.
        MarkerBlockEntity anchor = near.get(0);
        for (MarkerBlockEntity m : near) {
            if (m.getBlockPos().distManhattan(worldPosition) < anchor.getBlockPos().distManhattan(worldPosition)) anchor = m;
        }
        MarkerBlockEntity partner = anchor.validPartner(sl);
        if (partner == null) {
            partner = LeyMarkerBlock.nearestUnpaired(sl, anchor.getBlockPos(), max);
            if (partner == null) {
                fail(feedback, Component.translatable("message.ars_source_quarry.no_partner", max));
                return null;
            }
            LeyMarkerBlock.pair(anchor, partner);
        }
        BlockPos a = anchor.getBlockPos(), b = partner.getBlockPos();
        int w = Math.abs(a.getX() - b.getX()) + 1, l = Math.abs(a.getZ() - b.getZ()) + 1;
        if (w > max || l > max) {
            fail(feedback, Component.translatable("message.ars_source_quarry.too_big", w, l, max));
            return null;
        }
        if (w < 3 || l < 3) {   // the boundary itself is never mined, so 3x3 is the smallest useful area
            fail(feedback, Component.translatable("message.ars_source_quarry.too_small", w, l));
            return null;
        }
        return new BlockPos[]{a, b};
    }

    private static List<MarkerBlockEntity> markersAround(ServerLevel sl, BlockPos center, int range) {
        List<MarkerBlockEntity> out = new ArrayList<>(4);
        for (int chx = (center.getX() - range) >> 4; chx <= (center.getX() + range) >> 4; chx++) {
            for (int chz = (center.getZ() - range) >> 4; chz <= (center.getZ() + range) >> 4; chz++) {
                if (!sl.hasChunk(chx, chz)) continue;
                LevelChunk chunk = sl.getChunk(chx, chz);
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (!(be instanceof MarkerBlockEntity m)) continue;
                    BlockPos mp = m.getBlockPos();
                    if (Math.abs(mp.getX() - center.getX()) <= range && Math.abs(mp.getY() - center.getY()) <= range
                            && Math.abs(mp.getZ() - center.getZ()) <= range) out.add(m);
                }
            }
        }
        return out;
    }

    private void fail(@Nullable ServerPlayer feedback, Component why) {
        linkError = why;
        status = Status.NOT_LINKED;
        setChanged();
        if (feedback != null) feedback.sendSystemMessage(why.copy().withStyle(net.minecraft.ChatFormatting.RED));
    }

    // =====================================================================================
    // Owner lock
    // =====================================================================================

    public void setOwner(Player player) {
        owner = player.getUUID();
        ownerName = player.getGameProfile().getName();
        locked = true;
        setChanged();
    }

    public String getOwnerName() {
        return owner == null ? "" : ownerName;
    }

    /** Owner, operators and the owner's scoreboard team; anyone when unlocked or unowned. */
    public boolean canAccess(Player player) {
        if (!locked || owner == null) return true;
        return canChangeLock(player) || sameTeam(player);
    }

    private boolean canChangeLock(Player player) {
        return owner == null || owner.equals(player.getUUID()) || player.hasPermissions(2);
    }

    private boolean sameTeam(Player player) {
        if (level == null || ownerName.isEmpty()) return false;
        PlayerTeam ownerTeam = level.getScoreboard().getPlayersTeam(ownerName);
        return ownerTeam != null && ownerTeam == level.getScoreboard().getPlayersTeam(player.getScoreboardName());
    }

    /** What Apply would do right now. */
    private int applyKind() {
        if (!linked) return QuarryData.APPLY_RESTART;
        if (up == appliedUp && down == appliedDown) return QuarryData.APPLY_NONE;
        if (up == appliedUp && down > appliedDown) return QuarryData.APPLY_EXTEND;
        return QuarryData.APPLY_RESTART;
    }

    private void apply(ServerPlayer player) {
        if (!(level instanceof ServerLevel sl)) return;
        if (applyKind() == QuarryData.APPLY_EXTEND && markerA != null && markerB != null) {
            // Digging deeper keeps progress. It uses the stored corners, so it still works after the
            // markers were collected. The cursor is already at or just below the old bottom layer.
            minY = Math.min(markerA.getY(), markerB.getY()) - down;
            appliedDown = down;
            done = false;
            status = Status.RUNNING;
            setChanged();
            syncToClients();
            updateComparator();
            return;
        }
        link(player);
    }

    /** Button presses from the screen; validated here on the server. */
    public void handleButton(int id, ServerPlayer player) {
        if (level == null || !canAccess(player)) return;
        int base = baseY();
        int maxUp = level.getMaxBuildHeight() - 1 - base;
        int maxDown = base - level.getMinBuildHeight();
        switch (id) {
            case QuarryData.B_UP_MINUS -> up = Math.max(0, up - 1);
            case QuarryData.B_UP_PLUS -> up = Math.min(maxUp, up + 1);
            case QuarryData.B_UP_MINUS10 -> up = Math.max(0, up - 10);
            case QuarryData.B_UP_PLUS10 -> up = Math.min(maxUp, up + 10);
            case QuarryData.B_DOWN_MINUS -> down = Math.max(0, down - 1);
            case QuarryData.B_DOWN_PLUS -> down = Math.min(maxDown, down + 1);
            case QuarryData.B_DOWN_MINUS10 -> down = Math.max(0, down - 10);
            case QuarryData.B_DOWN_PLUS10 -> down = Math.min(maxDown, down + 10);
            case QuarryData.B_BEDROCK -> down = maxDown;
            case QuarryData.B_APPLY -> apply(player);
            case QuarryData.B_REDSTONE -> redstoneMode = RedstoneMode.values()[(redstoneMode.ordinal() + 1) % 4];
            case QuarryData.B_REDSTONE_BACK -> redstoneMode = RedstoneMode.values()[(redstoneMode.ordinal() + 3) % 4];
            case QuarryData.B_OUTLINE -> {
                outline = !outline;
                syncToClients();
            }
            case QuarryData.B_LOCK -> {
                if (!canChangeLock(player)) return;
                if (owner == null) setOwner(player); else locked = !locked;   // an unowned quarry is claimed
            }
            case QuarryData.B_VOID_MODE -> voidWhitelist = !voidWhitelist;
            case QuarryData.B_VOID_TAGS -> {
                voidTags = !voidTags;
                voidTagSet = null;
            }
            default -> { return; }
        }
        setChanged();
    }

    /** Marker level if linked; otherwise the quarry's own height (used only to bound the buttons). */
    private int baseY() {
        if (markerA != null && markerB != null) return Math.min(markerA.getY(), markerB.getY());
        return worldPosition.getY();
    }

    public void setPowered(boolean powered) {
        this.powered = powered;
    }

    // =====================================================================================
    // Ticking
    // =====================================================================================

    public void serverTick(ServerLevel sl) {
        long now = sl.getGameTime();
        if ((forcedQuarryChunk || forcedAreaChunk != Long.MIN_VALUE || QuarryConfig.CHUNK_LOADING.get()) && now >= nextUpkeep) {
            nextUpkeep = now + 20;
            manageChunks(sl);
        }
        // Tell nearby clients when mining starts or stops (for the hum), at most once every 2 seconds.
        boolean running = linked && !done && status == Status.RUNNING;
        if (running != syncedRunning && now >= nextRunningSync) {
            syncedRunning = running;
            nextRunningSync = now + 40;
            syncToClients();
        }
        // Cheapest checks first: an idle quarry costs a few field reads per tick.
        if (!linked) {
            if (!linkTried) link(null);   // placed by a command or structure: try once on its own
            return;
        }
        if (done) return;
        if (!redstoneAllows()) {
            status = redstoneMode == RedstoneMode.OFF ? Status.DISABLED : Status.WAITING_REDSTONE;
            return;
        }
        if (QuarryConfig.PAUSE_FOR_PLAYERS.get()) {
            if (now >= nextPlayerCheck) {
                nextPlayerCheck = now + 20;
                playerInArea = anyPlayerInArea(sl);
            }
            if (playerInArea) {
                status = Status.PLAYER_IN_AREA;
                return;
            }
        }
        if (!pending.isEmpty()) {
            if (--flushTimer > 0) return;
            flushTimer = 10;
            flushPending();
            if (!pending.isEmpty()) {
                status = Status.OUTPUT_FULL;
                return;
            }
        }
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        ensureGlyphs(sl);

        int budget = QuarryConfig.SCAN_PER_TICK.get();
        int actions = 1 + amplify;   // each Amplify glyph mines one more block per action
        while (budget-- > 0) {
            BlockPos target;
            boolean fromQueue = !recheck.isEmpty();
            if (fromQueue) {
                target = cursorPos.set(recheck.peekFirst());
            } else if (cy < minY) {
                tickFinishing(sl);
                return;
            } else {
                target = cursorPos.set(cx, cy, cz);
            }
            if (!sl.isLoaded(target)) {
                if (forcedQuarryChunk) {
                    forceAreaChunk(sl, target);   // chunk loading is on: load just the chunk being mined
                    wait(Status.WAITING_CHUNK, 5);
                } else {
                    wait(Status.WAITING_CHUNK, 40);
                }
                return;
            }
            Outcome o = process(sl, target);
            if (o == Outcome.WAIT) return;
            if (fromQueue) recheck.pollFirst(); else advance(sl);
            if (o == Outcome.WORKED && --actions <= 0) {
                status = Status.RUNNING;
                cooldown = QuarryConfig.TICKS_PER_BLOCK.get() - 1;
                setChanged();
                return;
            }
        }
        status = Status.RUNNING;
    }

    private boolean redstoneAllows() {
        return switch (redstoneMode) {
            case ALWAYS -> true;
            case HIGH -> powered;
            case LOW -> !powered;
            case OFF -> false;
        };
    }

    private boolean anyPlayerInArea(ServerLevel sl) {
        for (ServerPlayer p : sl.players()) {
            if (p.isSpectator()) continue;
            double x = p.getX(), y = p.getY(), z = p.getZ();
            if (x >= minX && x < maxX + 1 && z >= minZ && z < maxZ + 1 && y >= minY - 1 && y < maxY + 2) return true;
        }
        return false;
    }

    private void wait(Status why, int ticks) {
        status = why;
        cooldown = ticks;
    }

    private void advance(ServerLevel sl) {
        if (++cx <= maxX) return;
        cx = minX;
        if (++cz <= maxZ) return;
        cz = minZ;
        layerDone(sl, cy);
        cy--;
        setChanged();
        updateComparator();
    }

    /** After each layer, things that fell into already-cleared columns are queued for mining. */
    private void layerDone(ServerLevel sl, int layerY) {
        if (dirtyColumns.isEmpty()) return;
        // A column stays watched while something is still falling over the area, however fast layers go.
        boolean falling = fallingOverArea(sl);
        Iterator<Map.Entry<Long, Integer>> it = dirtyColumns.entrySet().iterator();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        while (it.hasNext()) {
            Map.Entry<Long, Integer> e = it.next();
            int x = colX(e.getKey()), z = colZ(e.getKey());
            for (int y = maxY; y >= layerY; y--) {
                m.set(x, y, z);
                if (sl.isLoaded(m) && !sl.getBlockState(m).isAir()) recheck.addLast(m.asLong());
            }
            if (falling || sl.getBlockState(m.set(x, maxY + 1, z)).getBlock() instanceof FallingBlock) e.setValue(2);
            else if (e.getValue() <= 1) it.remove();
            else e.setValue(e.getValue() - 1);
        }
    }

    /**
     * Whether a falling block (sand, gravel...) is in the air over the area. One entity search over the
     * area, and only ever run while a column is being watched, which is rare.
     */
    private boolean fallingOverArea(ServerLevel sl) {
        AABB box = new AABB(minX, minY, minZ, maxX + 1, maxY + 48, maxZ + 1);
        return !sl.getEntitiesOfClass(FallingBlockEntity.class, box).isEmpty();
    }

    /** Below the bottom layer: wait until nothing is falling, recheck the watched columns, then stop. */
    private void tickFinishing(ServerLevel sl) {
        status = Status.FINISHING;
        if (!dirtyColumns.isEmpty()) {
            if (finishTimer == 0) {
                finishTimer = 40;
                return;
            }
            if (--finishTimer > 0) return;
            if (fallingOverArea(sl)) {
                finishTimer = 20;
                return;
            }
            layerDone(sl, minY);
            if (!recheck.isEmpty()) return;
            dirtyColumns.clear();   // nothing falling and nothing left to mine in them
        }
        finish(sl);
    }

    private void finish(ServerLevel sl) {
        done = true;
        status = Status.DONE;
        if (QuarryConfig.COLLECT_MARKERS.get()) {
            for (BlockPos m : new BlockPos[]{markerA, markerB}) {
                if (m == null || !sl.isLoaded(m) || !LeyMarkerBlock.isMarker(sl.getBlockState(m))) continue;
                if (insert(new ItemStack(ArsSourceQuarry.LEY_MARKER_ITEM.get())).isEmpty()) {
                    sl.setBlock(m, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
        setChanged();
        updateComparator();
    }

    // =====================================================================================
    // Mining one position
    // =====================================================================================

    private Outcome process(ServerLevel sl, BlockPos target) {
        BlockState state = sl.getBlockState(target);
        if (state.isAir()) return Outcome.SKIPPED;
        FluidState fluid = state.getFluidState();

        if (state.getBlock() instanceof LiquidBlock) {
            // Flowing fluid is left alone: it drains by itself once its sources are gone.
            return fluid.isSource() ? removeFluidBlock(sl, target, fluid) : Outcome.SKIPPED;
        }
        if (state.hasBlockEntity()) {
            if (!QuarryConfig.MINE_BLOCK_ENTITIES.get()) return skip();
            if (LeyMarkerBlock.isMarker(state) || state.is(ArsSourceQuarry.SOURCE_QUARRY.get())) return skip();
        }
        float hardness = state.getDestroySpeed(sl, target);
        if (hardness < 0 || hardness > QuarryConfig.MAX_HARDNESS.get() || state.is(ArsSourceQuarry.QUARRY_IMMUNE)) {
            return skip();
        }

        // Waterlogged blocks: the water is collected (or evaporated) along with the block.
        boolean wet = !fluid.isEmpty() && fluid.isSource();
        FluidStack water = wet ? new FluidStack(fluid.getType(), 1000) : FluidStack.EMPTY;
        IFluidHandler tank = wet ? findTank(water) : null;
        int cost = blockCost(hardness) + (wet ? fluidCost(tank) : 0);

        if (!hasItemOutput()) {
            wait(Status.NO_OUTPUT, 20);
            return Outcome.WAIT;
        }
        if (!ensureSource(sl, cost)) {
            wait(Status.NO_SOURCE, 20);
            return Outcome.WAIT;
        }

        FakePlayer fp = FakePlayerFactory.get(sl, PROFILE);
        fp.setPos(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5);
        // Lets claim and protection mods veto the break.
        if (NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(sl, target, state, fp)).isCanceled()) return skip();

        BlockEntity be = state.hasBlockEntity() ? sl.getBlockEntity(target) : null;
        List<ItemStack> drops = Block.getDrops(state, sl, target, be, fp, tool(sl));
        if (tank != null) tank.fill(water, IFluidHandler.FluidAction.EXECUTE);

        sl.levelEvent(2001, target, Block.getId(state));   // vanilla break particles + sound
        sl.setBlock(target, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        buffer -= cost;
        spent += cost;
        mined++;
        for (ItemStack drop : drops) output(sl, drop);
        if (!pending.isEmpty()) flushTimer = 10;
        effects(sl, target);
        noteGravity(sl, target);
        return Outcome.WORKED;
    }

    /** A block left in place on purpose (machine, unbreakable, too hard, protected); shown in the screen. */
    private Outcome skip() {
        skipped++;
        return Outcome.SKIPPED;
    }

    /** Crush, then Smelt, then the void filter, then the output. */
    private void output(ServerLevel sl, ItemStack drop) {
        List<ItemStack> stage = new ArrayList<>(2);
        stage.add(drop);
        if (crush) {
            List<ItemStack> crushed = new ArrayList<>(2);
            for (ItemStack s : stage) {
                CrushRecipe r = crushRecipe(sl, s);
                if (r == null) {
                    crushed.add(s);
                    continue;
                }
                for (int i = 0; i < s.getCount(); i++) crushed.addAll(r.getRolledOutputs(sl.random));
            }
            stage = crushed;
        }
        for (ItemStack s : stage) {
            if (s.isEmpty()) continue;
            if (smelt) {
                ItemStack res = smeltResult(sl, s);
                if (!res.isEmpty()) s = res.copyWithCount(Math.min(res.getMaxStackSize(), res.getCount() * s.getCount()));
            }
            if (discard(s)) continue;
            ItemStack left = insert(s);
            if (!left.isEmpty()) pending.add(left);
        }
    }

    private Outcome removeFluidBlock(ServerLevel sl, BlockPos target, FluidState fluid) {
        FluidStack stack = new FluidStack(fluid.getType(), 1000);
        IFluidHandler tank = findTank(stack);
        int cost = fluidCost(tank);
        if (!ensureSource(sl, cost)) {
            wait(Status.NO_SOURCE, 20);
            return Outcome.WAIT;
        }
        if (tank != null) tank.fill(stack, IFluidHandler.FluidAction.EXECUTE);
        sl.setBlock(target, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        buffer -= cost;
        spent += cost;
        if (QuarryConfig.EFFECTS.get()) {
            sl.sendParticles(glowParticle(), target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5,
                    2, 0.3, 0.3, 0.3, 0.0);
        }
        return Outcome.WORKED;
    }

    private void noteGravity(ServerLevel sl, BlockPos target) {
        if (target.getY() != maxY) return;
        if (sl.getBlockState(target.above()).getBlock() instanceof FallingBlock) {
            dirtyColumns.put(colKey(target.getX(), target.getZ()), 2);
        }
    }

    private int blockCost(float hardness) {
        int base = QuarryConfig.BASE_COST.get() + (int) Math.ceil(hardness * QuarryConfig.COST_PER_HARDNESS.get());
        return (int) Math.ceil(base * costMultiplier) + extraCost;
    }

    private static int fluidCost(@Nullable IFluidHandler tank) {
        return tank != null ? QuarryConfig.PUMP_COST.get() : QuarryConfig.EVAPORATE_COST.get();
    }

    // =====================================================================================
    // Glyphs, tool, void filter, recipes
    // =====================================================================================

    private void glyphsChanged() {
        amplify = -1;   // recomputed lazily on the next tick
        tool = null;
        setChanged();
    }

    private void ensureGlyphs(ServerLevel sl) {
        if (amplify >= 0) return;
        amplify = glyphs.level(QuarryGlyphs.AMPLIFY);
        int fortune = glyphs.level(QuarryGlyphs.FORTUNE);
        boolean extract = glyphs.level(QuarryGlyphs.EXTRACT) > 0;
        smelt = glyphs.level(QuarryGlyphs.SMELT) > 0;
        crush = glyphs.level(QuarryGlyphs.CRUSH) > 0;
        costMultiplier = 1 + amplify * QuarryConfig.AMPLIFY_COST.get();
        extraCost = (extract ? QuarryConfig.EXTRACT_COST.get() : 0) + fortune * QuarryConfig.FORTUNE_COST.get()
                + (smelt ? QuarryConfig.SMELT_COST.get() : 0) + (crush ? QuarryConfig.CRUSH_COST.get() : 0);
        tool = null;
    }

    private ItemStack tool(ServerLevel sl) {
        if (tool == null) {
            // Drops are rolled as if mined with a netherite pickaxe carrying the glyphs' enchantments.
            ItemStack t = new ItemStack(Items.NETHERITE_PICKAXE);
            var enchants = sl.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            if (glyphs.level(QuarryGlyphs.EXTRACT) > 0) t.enchant(enchants.getOrThrow(Enchantments.SILK_TOUCH), 1);
            int fortune = glyphs.level(QuarryGlyphs.FORTUNE);
            if (fortune > 0) t.enchant(enchants.getOrThrow(Enchantments.FORTUNE), fortune);
            tool = t;
        }
        return tool;
    }

    /**
     * Void filter. Blacklist: listed items are destroyed. Whitelist: only listed items are kept.
     * With tag matching on, an item also matches when it shares a common (c:) tag with a filter item.
     * The item and tag sets are rebuilt only when the filter or its settings change.
     */
    private boolean discard(ItemStack s) {
        if (voidItems == null) {
            Set<Item> set = new HashSet<>();
            for (int i = 0; i < voidFilter.getSlots(); i++) {
                ItemStack f = voidFilter.getStackInSlot(i);
                if (!f.isEmpty()) set.add(f.getItem());
            }
            voidItems = set;
        }
        if (voidItems.isEmpty()) return false;   // an empty filter never discards, in either mode
        boolean match = voidItems.contains(s.getItem());
        if (!match && voidTags) {
            if (voidTagSet == null) {
                Set<TagKey<Item>> tags = new HashSet<>();
                for (int i = 0; i < voidFilter.getSlots(); i++) {
                    voidFilter.getStackInSlot(i).getTags()
                            .filter(t -> t.location().getNamespace().equals("c")).forEach(tags::add);
                }
                voidTagSet = tags;
            }
            match = !voidTagSet.isEmpty() && s.getTags().anyMatch(voidTagSet::contains);
        }
        return voidWhitelist != match;
    }

    private static void checkRecipeCache(ServerLevel sl) {
        RecipeManager rm = sl.getRecipeManager();
        if (rm != cachedRecipes) {   // recipes were reloaded
            cachedRecipes = rm;
            SMELT_CACHE.clear();
            CRUSH_CACHE.clear();
        }
    }

    private static ItemStack smeltResult(ServerLevel sl, ItemStack in) {
        checkRecipeCache(sl);
        return SMELT_CACHE.computeIfAbsent(in.getItem(), item -> sl.getRecipeManager()
                .getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(new ItemStack(item)), sl)
                .map(h -> h.value().getResultItem(sl.registryAccess()).copy()).orElse(ItemStack.EMPTY));
    }

    private static @Nullable CrushRecipe crushRecipe(ServerLevel sl, ItemStack in) {
        checkRecipeCache(sl);
        return CRUSH_CACHE.computeIfAbsent(in.getItem(), item -> sl.getRecipeManager()
                .getRecipeFor(RecipeRegistry.CRUSH_TYPE.get(), new SingleRecipeInput(new ItemStack(item)), sl)
                .map(h -> h.value())).orElse(null);
    }

    public QuarryGlyphs.Handler getGlyphs() {
        return glyphs;
    }

    public ItemStackHandler getVoidFilter() {
        return voidFilter;
    }

    // =====================================================================================
    // Source
    // =====================================================================================

    private boolean ensureSource(ServerLevel sl, int cost) {
        if (buffer >= cost) return true;
        long now = sl.getGameTime();
        if (now < nextRefill) return false;
        nextRefill = now + 20;   // Ars' provider search runs at most once a second

        int capacity = Math.max(QuarryConfig.BUFFER_CAPACITY.get(), cost);
        int need = capacity - buffer;
        boolean fx = QuarryConfig.EFFECTS.get();
        for (ISpecialSourceProvider provider : SourceUtil.canTakeSource(worldPosition, sl, QuarryConfig.SOURCE_RANGE.get())) {
            ISourceTile tile = provider.getSource();
            if (tile instanceof CreativeSourceJarTile) {
                buffer = capacity;
                if (fx) EntityFollowProjectile.spawn(sl, provider.getCurrentPos(), worldPosition);
                break;
            }
            int have = tile.getSource();
            int take = Math.min(need, have);
            if (take <= 0) continue;
            int got = have - tile.removeSource(take);
            if (got <= 0) continue;
            buffer += got;
            need -= got;
            if (fx) EntityFollowProjectile.spawn(sl, provider.getCurrentPos(), worldPosition);
            if (need <= 0) break;
        }
        setChanged();
        return buffer >= cost;
    }

    // =====================================================================================
    // Output
    // =====================================================================================

    private void ensureCaches() {
        if (itemOut != null || !(level instanceof ServerLevel sl)) return;
        itemOut = new ArrayList<>(6);
        fluidOut = new ArrayList<>(6);
        for (Direction d : Direction.values()) {
            BlockPos n = worldPosition.relative(d);
            itemOut.add(BlockCapabilityCache.create(Capabilities.ItemHandler.BLOCK, sl, n, d.getOpposite()));
            fluidOut.add(BlockCapabilityCache.create(Capabilities.FluidHandler.BLOCK, sl, n, d.getOpposite()));
        }
    }

    private boolean hasItemOutput() {
        ensureCaches();
        if (itemOut == null) return false;
        for (BlockCapabilityCache<IItemHandler, Direction> c : itemOut) if (c.getCapability() != null) return true;
        return false;
    }

    private ItemStack insert(ItemStack stack) {
        ensureCaches();
        if (itemOut == null) return stack;
        for (BlockCapabilityCache<IItemHandler, Direction> c : itemOut) {
            IItemHandler h = c.getCapability();
            if (h == null) continue;
            stack = ItemHandlerHelper.insertItemStacked(h, stack, false);
            if (stack.isEmpty()) return ItemStack.EMPTY;
        }
        return stack;
    }

    private @Nullable IFluidHandler findTank(FluidStack stack) {
        ensureCaches();
        if (fluidOut == null) return null;
        for (BlockCapabilityCache<IFluidHandler, Direction> c : fluidOut) {
            IFluidHandler h = c.getCapability();
            if (h != null && h.fill(stack, IFluidHandler.FluidAction.SIMULATE) >= stack.getAmount()) return h;
        }
        return null;
    }

    private void flushPending() {
        for (int i = pending.size() - 1; i >= 0; i--) {
            ItemStack left = insert(pending.get(i));
            if (left.isEmpty()) pending.remove(i); else pending.set(i, left);
        }
        setChanged();
    }

    /** Pending output plus the glyphs: what drops when the quarry is broken. */
    public List<ItemStack> takeDrops() {
        List<ItemStack> out = new ArrayList<>(pending);
        pending.clear();
        for (int i = 0; i < glyphs.getSlots(); i++) {
            ItemStack g = glyphs.getStackInSlot(i);
            if (!g.isEmpty()) out.add(g.copy());
        }
        return out;
    }

    // =====================================================================================
    // Chunk loading (opt-in)
    // =====================================================================================

    /** Runs once a second while chunk loading is enabled or tickets are still held. */
    private void manageChunks(ServerLevel sl) {
        boolean want = QuarryConfig.CHUNK_LOADING.get() && linked && !done && redstoneAllows();
        if (want) {
            int upkeep = QuarryConfig.CHUNK_LOAD_COST.get();
            if (ensureSource(sl, upkeep)) {
                buffer -= upkeep;
                spent += upkeep;
            } else {
                want = false;   // cannot pay: let the chunks go until Source arrives
            }
        }
        if (!want) {
            releaseChunks(sl);
            return;
        }
        if (!forcedQuarryChunk) {
            ChunkPos c = new ChunkPos(worldPosition);
            ArsSourceQuarry.TICKETS.forceChunk(sl, worldPosition, c.x, c.z, true, true);
            forcedQuarryChunk = true;
            setChanged();
        }
    }

    private void forceAreaChunk(ServerLevel sl, BlockPos target) {
        ChunkPos c = new ChunkPos(target);
        long key = c.toLong();
        if (key == forcedAreaChunk) return;
        if (forcedAreaChunk != Long.MIN_VALUE) {
            ChunkPos old = new ChunkPos(forcedAreaChunk);
            ArsSourceQuarry.TICKETS.forceChunk(sl, worldPosition, old.x, old.z, false, false);
        }
        ArsSourceQuarry.TICKETS.forceChunk(sl, worldPosition, c.x, c.z, true, false);
        forcedAreaChunk = key;
        setChanged();
    }

    /** Drops every chunk ticket this quarry holds. Also called when the block is broken. */
    public void releaseChunks(ServerLevel sl) {
        if (forcedQuarryChunk) {
            ChunkPos c = new ChunkPos(worldPosition);
            ArsSourceQuarry.TICKETS.forceChunk(sl, worldPosition, c.x, c.z, false, true);
            forcedQuarryChunk = false;
            setChanged();
        }
        if (forcedAreaChunk != Long.MIN_VALUE) {
            ChunkPos old = new ChunkPos(forcedAreaChunk);
            ArsSourceQuarry.TICKETS.forceChunk(sl, worldPosition, old.x, old.z, false, false);
            forcedAreaChunk = Long.MIN_VALUE;
            setChanged();
        }
    }

    // =====================================================================================
    // Source capability: relays can push Source in; nothing can take it out
    // =====================================================================================

    public ISourceCap getSourceCap() {
        return sourceCap;
    }

    private final class SourceCap implements ISourceCap {
        private int capacity() {
            return QuarryConfig.BUFFER_CAPACITY.get();
        }

        @Override
        public boolean canAcceptSource(int source) {
            return buffer < capacity();
        }

        @Override
        public boolean canProvideSource(int source) {
            return false;
        }

        @Override
        public int getMaxExtract() {
            return 0;
        }

        @Override
        public int getMaxReceive() {
            return capacity();
        }

        @Override
        public int getSource() {
            return buffer;
        }

        @Override
        public int getSourceCapacity() {
            return capacity();
        }

        @Override
        public void setSource(int source) {
            buffer = Math.max(0, Math.min(capacity(), source));
            setChanged();
        }

        @Override
        public void setMaxSource(int max) {
            // fixed by the config
        }

        @Override
        public int receiveSource(int source, boolean simulate) {
            int accepted = Math.max(0, Math.min(source, capacity() - buffer));
            if (!simulate && accepted > 0) {
                buffer += accepted;
                setChanged();
            }
            return accepted;
        }

        @Override
        public int extractSource(int source, boolean simulate) {
            return 0;
        }
    }

    // =====================================================================================
    // Comparator, effects
    // =====================================================================================

    public int getComparatorLevel() {
        return comparator;
    }

    private void updateComparator() {
        int v;
        if (!linked) v = 0;
        else if (done) v = 15;
        else v = Math.min(14, (int) (progressPermille() * 15L / 1000));
        if (v != comparator) {
            comparator = v;
            if (level != null) level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        }
    }

    private void effects(ServerLevel sl, BlockPos target) {
        if (!QuarryConfig.EFFECTS.get()) return;
        sl.sendParticles(glowParticle(), target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5,
                4, 0.3, 0.3, 0.3, 0.0);
        long now = sl.getGameTime();
        if (now >= nextBeam) {   // at most one Source trail every half second
            nextBeam = now + 10;
            EntityFollowProjectile.spawn(sl, target.immutable(), worldPosition);
        }
    }

    private static ColorParticleTypeData glowParticle() {
        if (glow == null) glow = new ColorParticleTypeData(new ParticleColor(200, 60, 255), false, 0.3f, 0.75f, 24);
        return glow;
    }

    // =====================================================================================
    // Screen
    // =====================================================================================

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.ars_source_quarry.source_quarry");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        // Per-viewer data: whether this player may change the lock.
        ContainerData viewerData = QuarryData.split(i -> i == QuarryData.CAN_LOCK ? (canChangeLock(player) ? 1 : 0) : dataValue(i));
        return new SourceQuarryMenu(id, inv, this, viewerData);
    }

    private long totalPositions() {
        return (long) (maxX - minX + 1) * (maxZ - minZ + 1) * (maxY - minY + 1);
    }

    private long passedPositions() {
        if (done || cy < minY) return totalPositions();
        long area = (long) (maxX - minX + 1) * (maxZ - minZ + 1);
        return (long) (maxY - cy) * area + (long) (cz - minZ) * (maxX - minX + 1) + (cx - minX);
    }

    private int progressPermille() {
        long total = totalPositions();
        return total <= 0 ? 1000 : (int) Math.min(1000, passedPositions() * 1000 / total);
    }

    /** Values shown by the screen; only read while a player has it open. */
    private int dataValue(int i) {
        return switch (i) {
            case QuarryData.STATUS -> status.ordinal();
            case QuarryData.PROGRESS -> linked ? progressPermille() : 0;
            case QuarryData.BUFFER -> buffer;
            case QuarryData.CAPACITY -> QuarryConfig.BUFFER_CAPACITY.get();
            case QuarryData.COST -> {
                if (level instanceof ServerLevel sl) ensureGlyphs(sl);
                yield blockCost(1.5f);   // stone
            }
            case QuarryData.LAYER -> Math.max(cy, minY);
            case QuarryData.AX -> markerA != null ? markerA.getX() : 0;
            case QuarryData.AY -> markerA != null ? markerA.getY() : 0;
            case QuarryData.AZ -> markerA != null ? markerA.getZ() : 0;
            case QuarryData.BX -> markerB != null ? markerB.getX() : 0;
            case QuarryData.BY -> markerB != null ? markerB.getY() : 0;
            case QuarryData.BZ -> markerB != null ? markerB.getZ() : 0;
            case QuarryData.LINKED -> linked ? 1 : 0;
            case QuarryData.UP -> up;
            case QuarryData.DOWN -> down;
            case QuarryData.APPLY -> applyKind();
            case QuarryData.REDSTONE -> redstoneMode.ordinal();
            case QuarryData.OUTLINE -> outline ? 1 : 0;
            case QuarryData.ESTIMATE -> {
                long passed = passedPositions();
                long remaining = totalPositions() - passed;
                yield passed <= 0 || done ? 0 : (int) Math.min(Integer.MAX_VALUE, spent * remaining / passed);
            }
            case QuarryData.BASE_Y -> baseY();
            case QuarryData.MINED -> (int) Math.min(Integer.MAX_VALUE, mined);
            case QuarryData.LOCKED -> locked && owner != null ? 1 : 0;
            case QuarryData.VOID_WHITELIST -> voidWhitelist ? 1 : 0;
            case QuarryData.VOID_TAGS -> voidTags ? 1 : 0;
            case QuarryData.SKIPPED -> (int) Math.min(Integer.MAX_VALUE, skipped);
            case QuarryData.CHUNK_LOADED -> forcedQuarryChunk ? 1 : 0;
            default -> 0;
        };
    }

    /** For the optional Jade tooltip; called only while a player is looking at this quarry. */
    public void writeJadeData(CompoundTag tag) {
        tag.putInt("status", status.ordinal());
        tag.putBoolean("linked", linked);
        tag.putInt("progress", linked ? progressPermille() : 0);
        tag.putInt("layer", Math.max(cy, minY));
        tag.putInt("buffer", buffer);
        tag.putInt("capacity", QuarryConfig.BUFFER_CAPACITY.get());
    }

    // =====================================================================================
    // Client sync (outline only)
    // =====================================================================================

    private void syncToClients() {
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("linked", linked);
        tag.putBoolean("outline", outline);
        tag.putBoolean("running", syncedRunning);
        tag.putIntArray("box", new int[]{minX, minY, minZ, maxX, maxY, maxZ});
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        readClient(tag);
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, ClientboundBlockEntityDataPacket pkt,
                             HolderLookup.Provider registries) {
        readClient(pkt.getTag());
    }

    private void readClient(CompoundTag tag) {
        clientLinked = tag.getBoolean("linked");
        clientOutline = tag.getBoolean("outline");
        clientRunning = tag.getBoolean("running");
        int[] box = tag.getIntArray("box");
        if (box.length == 6) clientBox = box;
    }

    public boolean clientShowsOutline() {
        return clientLinked && clientOutline;
    }

    public boolean clientRunning() {
        return clientRunning;
    }

    public boolean clientLinked() {
        return clientLinked;
    }

    /** minX, minY, minZ, maxX, maxY, maxZ as last sent by the server. */
    public int[] clientBox() {
        return clientBox;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && level.isClientSide) ClientQuarries.add(this);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && level.isClientSide) ClientQuarries.remove(this);
    }

    // =====================================================================================
    // Save / load
    // =====================================================================================

    private static long colKey(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    private static int colX(long k) {
        return (int) (k >> 32);
    }

    private static int colZ(long k) {
        return (int) k;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("linked", linked);
        tag.putBoolean("done", done);
        tag.putIntArray("box", new int[]{minX, minY, minZ, maxX, maxY, maxZ});
        tag.putIntArray("cursor", new int[]{cx, cy, cz});
        tag.putInt("buffer", buffer);
        tag.putLong("mined", mined);
        tag.putLong("spent", spent);
        tag.putInt("up", up);
        tag.putInt("down", down);
        tag.putInt("appliedUp", appliedUp);
        tag.putInt("appliedDown", appliedDown);
        if (markerA != null) tag.putLong("markerA", markerA.asLong());
        if (markerB != null) tag.putLong("markerB", markerB.asLong());
        tag.putString("redstone", redstoneMode.name());
        tag.putBoolean("outline", outline);
        if (owner != null) {
            tag.putUUID("owner", owner);
            tag.putString("ownerName", ownerName);
        }
        tag.putBoolean("locked", locked);
        tag.putBoolean("voidWhitelist", voidWhitelist);
        tag.putBoolean("voidTags", voidTags);
        tag.putLong("skipped", skipped);
        tag.putBoolean("forcedQuarryChunk", forcedQuarryChunk);
        if (forcedAreaChunk != Long.MIN_VALUE) tag.putLong("forcedAreaChunk", forcedAreaChunk);
        tag.put("Glyphs", glyphs.serializeNBT(registries));
        tag.put("Void", voidFilter.serializeNBT(registries));
        if (!pending.isEmpty()) {
            ListTag list = new ListTag();
            for (ItemStack s : pending) list.add(s.save(registries));
            tag.put("pending", list);
        }
        if (!dirtyColumns.isEmpty()) {
            long[] cols = new long[dirtyColumns.size() * 2];
            int i = 0;
            for (Map.Entry<Long, Integer> e : dirtyColumns.entrySet()) {
                cols[i++] = e.getKey();
                cols[i++] = e.getValue();
            }
            tag.putLongArray("dirty", cols);
        }
        if (!recheck.isEmpty()) tag.putLongArray("recheck", recheck.stream().mapToLong(Long::longValue).toArray());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        linked = tag.getBoolean("linked");
        done = tag.getBoolean("done");
        int[] box = tag.getIntArray("box");
        if (box.length == 6) {
            minX = box[0]; minY = box[1]; minZ = box[2];
            maxX = box[3]; maxY = box[4]; maxZ = box[5];
        } else linked = false;
        int[] cur = tag.getIntArray("cursor");
        if (cur.length == 3) {
            cx = cur[0]; cy = cur[1]; cz = cur[2];
        }
        buffer = tag.getInt("buffer");
        mined = tag.getLong("mined");
        spent = tag.getLong("spent");
        if (tag.contains("up")) up = tag.getInt("up");
        if (tag.contains("down")) down = tag.getInt("down");
        appliedUp = tag.getInt("appliedUp");
        appliedDown = tag.getInt("appliedDown");
        markerA = tag.contains("markerA") ? BlockPos.of(tag.getLong("markerA")) : null;
        markerB = tag.contains("markerB") ? BlockPos.of(tag.getLong("markerB")) : null;
        try {
            redstoneMode = RedstoneMode.valueOf(tag.getString("redstone"));
        } catch (IllegalArgumentException e) {
            redstoneMode = RedstoneMode.ALWAYS;
        }
        outline = tag.getBoolean("outline");
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        ownerName = tag.getString("ownerName");
        locked = !tag.contains("locked") || tag.getBoolean("locked");
        voidWhitelist = tag.getBoolean("voidWhitelist");
        voidTags = tag.getBoolean("voidTags");
        skipped = tag.getLong("skipped");
        forcedQuarryChunk = tag.getBoolean("forcedQuarryChunk");
        forcedAreaChunk = tag.contains("forcedAreaChunk") ? tag.getLong("forcedAreaChunk") : Long.MIN_VALUE;
        voidTagSet = null;
        if (tag.contains("Glyphs")) glyphs.deserializeNBT(registries, tag.getCompound("Glyphs"));
        if (tag.contains("Void")) voidFilter.deserializeNBT(registries, tag.getCompound("Void"));
        amplify = -1;
        voidItems = null;
        pending.clear();
        ListTag list = tag.getList("pending", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            ItemStack s = ItemStack.parseOptional(registries, list.getCompound(i));
            if (!s.isEmpty()) pending.add(s);
        }
        dirtyColumns.clear();
        long[] cols = tag.getLongArray("dirty");
        for (int i = 0; i + 1 < cols.length; i += 2) dirtyColumns.put(cols[i], (int) cols[i + 1]);
        recheck.clear();
        for (long l : tag.getLongArray("recheck")) recheck.addLast(l);
        status = !linked ? Status.NOT_LINKED : done ? Status.DONE : Status.RUNNING;
        comparator = !linked ? 0 : done ? 15 : Math.min(14, (int) (progressPermille() * 15L / 1000));
    }
}
