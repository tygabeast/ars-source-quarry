package com.tygabeast.arssourcequarry.block;

import com.mojang.serialization.MapCodec;
import com.tygabeast.arssourcequarry.ArsSourceQuarry;
import com.tygabeast.arssourcequarry.QuarryConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Two Ley Markers set the corners of a quarry area. A newly placed marker pairs with the nearest
 * unpaired marker within the maximum area size; breaking one unpairs the other. The pairing search
 * reads chunk block-entity maps and runs once per placement.
 */
public class LeyMarkerBlock extends BaseEntityBlock {
    public static final MapCodec<LeyMarkerBlock> CODEC = simpleCodec(LeyMarkerBlock::new);
    private static final VoxelShape SHAPE = Block.box(5, 0, 6, 11, 11, 10);

    public LeyMarkerBlock(Properties props) {
        super(props);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MarkerBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!(level instanceof ServerLevel sl) || !(level.getBlockEntity(pos) instanceof MarkerBlockEntity self)) return;
        MarkerBlockEntity other = nearestUnpaired(sl, pos, QuarryConfig.MAX_SIZE.get());
        if (placer instanceof Player p) {
            p.displayClientMessage(other == null
                    ? Component.translatable("message.ars_source_quarry.marker_waiting").withStyle(ChatFormatting.GRAY)
                    : Component.translatable("message.ars_source_quarry.marker_paired",
                            Math.abs(pos.getX() - other.getBlockPos().getX()) + 1,
                            Math.abs(pos.getZ() - other.getBlockPos().getZ()) + 1).withStyle(ChatFormatting.LIGHT_PURPLE), true);
        }
        if (other != null) pair(self, other);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide
                && level.getBlockEntity(pos) instanceof MarkerBlockEntity self) {
            MarkerBlockEntity other = self.validPartner(level);
            if (other != null) other.setPartner(null);
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    public static void pair(MarkerBlockEntity a, MarkerBlockEntity b) {
        a.setPartner(b.getBlockPos());
        b.setPartner(a.getBlockPos());
    }

    /** Nearest marker within range on every axis that has no valid partner. */
    static @Nullable MarkerBlockEntity nearestUnpaired(ServerLevel sl, BlockPos center, int range) {
        MarkerBlockEntity best = null;
        long bestDist = Long.MAX_VALUE;
        for (int chx = (center.getX() - range) >> 4; chx <= (center.getX() + range) >> 4; chx++) {
            for (int chz = (center.getZ() - range) >> 4; chz <= (center.getZ() + range) >> 4; chz++) {
                if (!sl.hasChunk(chx, chz)) continue;
                LevelChunk chunk = sl.getChunk(chx, chz);
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (!(be instanceof MarkerBlockEntity m) || m.getBlockPos().equals(center)) continue;
                    BlockPos mp = m.getBlockPos();
                    if (Math.abs(mp.getX() - center.getX()) > range || Math.abs(mp.getY() - center.getY()) > range
                            || Math.abs(mp.getZ() - center.getZ()) > range) continue;
                    if (m.validPartner(sl) != null) continue;
                    long d = mp.distManhattan(center);
                    if (d < bestDist) {
                        bestDist = d;
                        best = m;
                    }
                }
            }
        }
        return best;
    }

    public static boolean isMarker(BlockState state) {
        return state.is(ArsSourceQuarry.LEY_MARKER.get());
    }
}
