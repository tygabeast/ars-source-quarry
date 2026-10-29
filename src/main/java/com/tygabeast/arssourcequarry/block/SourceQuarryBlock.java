package com.tygabeast.arssourcequarry.block;

import com.mojang.serialization.MapCodec;
import com.tygabeast.arssourcequarry.ArsSourceQuarry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class SourceQuarryBlock extends BaseEntityBlock {
    public static final MapCodec<SourceQuarryBlock> CODEC = simpleCodec(SourceQuarryBlock::new);

    /** The open front faces the player who placed it; the cloth drapes over the left and right sides. */
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    public SourceQuarryBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
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
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SourceQuarryBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        // Server only: the client never runs quarry logic.
        if (level.isClientSide) return null;
        return createTickerHelper(type, ArsSourceQuarry.QUARRY_BE.get(),
                (lvl, pos, st, be) -> be.serverTick((ServerLevel) lvl));
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel && level.getBlockEntity(pos) instanceof SourceQuarryBlockEntity be) {
            if (placer instanceof Player p) be.setOwner(p);
            be.setPowered(level.hasNeighborSignal(pos));
            be.link(placer instanceof ServerPlayer sp ? sp : null);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof SourceQuarryBlockEntity be && player instanceof ServerPlayer sp) {
            if (!be.canAccess(sp)) {
                sp.displayClientMessage(Component.translatable("message.ars_source_quarry.locked", be.getOwnerName())
                        .withStyle(ChatFormatting.RED), true);
                return InteractionResult.CONSUME;
            }
            sp.openMenu(be, buf -> {
                buf.writeBlockPos(pos);
                buf.writeUtf(be.getOwnerName());
            });
        }
        return InteractionResult.CONSUME;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos from, boolean moving) {
        super.neighborChanged(state, level, pos, block, from, moving);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof SourceQuarryBlockEntity be) {
            be.setPowered(level.hasNeighborSignal(pos));
        }
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof SourceQuarryBlockEntity be ? be.getComparatorLevel() : 0;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof SourceQuarryBlockEntity be) {
            if (level instanceof ServerLevel sl) be.releaseChunks(sl);
            // Glyphs and items waiting for room in the output are dropped rather than lost.
            for (ItemStack s : be.takeDrops()) Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), s);
        }
        super.onRemove(state, level, pos, newState, moving);
    }
}
