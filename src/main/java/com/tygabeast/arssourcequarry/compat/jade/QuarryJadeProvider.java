package com.tygabeast.arssourcequarry.compat.jade;

import com.tygabeast.arssourcequarry.ArsSourceQuarry;
import com.tygabeast.arssourcequarry.block.SourceQuarryBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * Status, progress and Source of the quarry the player is looking at. The server side runs only while
 * a player has the quarry under their crosshair, and sends five small numbers.
 */
public enum QuarryJadeProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    private static final ResourceLocation UID = ArsSourceQuarry.id("quarry_info");

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (accessor.getBlockEntity() instanceof SourceQuarryBlockEntity quarry) quarry.writeJadeData(data);
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (!data.contains("status")) return;
        SourceQuarryBlockEntity.Status[] statuses = SourceQuarryBlockEntity.Status.values();
        int s = data.getInt("status");
        SourceQuarryBlockEntity.Status status = s >= 0 && s < statuses.length ? statuses[s] : SourceQuarryBlockEntity.Status.NOT_LINKED;
        tooltip.add(status == SourceQuarryBlockEntity.Status.RUNNING
                ? Component.translatable("gui.ars_source_quarry.mining_layer", data.getInt("layer"))
                : Component.translatable(status.key()));
        if (data.getBoolean("linked")) {
            tooltip.add(Component.translatable("jade.ars_source_quarry.progress", data.getInt("progress") / 10)
                    .withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("gui.ars_source_quarry.tip.source", data.getInt("buffer"), data.getInt("capacity"))
                .withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
