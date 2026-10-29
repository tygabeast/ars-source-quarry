package com.tygabeast.arssourcequarry.block;

import com.tygabeast.arssourcequarry.ArsSourceQuarry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Never ticks. Holds the position of the marker it is paired with, so several marked areas can sit
 * close together without the quarry mixing up their corners. Synced to clients for the outline.
 */
public class MarkerBlockEntity extends BlockEntity {
    private @Nullable BlockPos partner;

    public MarkerBlockEntity(BlockPos pos, BlockState state) {
        super(ArsSourceQuarry.MARKER_BE.get(), pos, state);
    }

    public @Nullable BlockPos getPartner() {
        return partner;
    }

    public void setPartner(@Nullable BlockPos partner) {
        if (java.util.Objects.equals(this.partner, partner)) return;
        this.partner = partner == null ? null : partner.immutable();
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    /** The partner, only if it is still a loaded marker that points back at this one. */
    public @Nullable MarkerBlockEntity validPartner(Level level) {
        if (partner == null || !level.isLoaded(partner)) return null;
        return level.getBlockEntity(partner) instanceof MarkerBlockEntity m && worldPosition.equals(m.partner) ? m : null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (partner != null) tag.putLong("partner", partner.asLong());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        partner = tag.contains("partner") ? BlockPos.of(tag.getLong("partner")) : null;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        if (partner != null) tag.putLong("partner", partner.asLong());
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
