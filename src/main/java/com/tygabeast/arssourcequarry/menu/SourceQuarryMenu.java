package com.tygabeast.arssourcequarry.menu;

import com.tygabeast.arssourcequarry.ArsSourceQuarry;
import com.tygabeast.arssourcequarry.block.QuarryData;
import com.tygabeast.arssourcequarry.block.QuarryGlyphs;
import com.tygabeast.arssourcequarry.block.SourceQuarryBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Slots: 0-4 glyphs, 5-10 void filter (ghost slots: they record an item without taking it),
 * 11-46 player inventory. All screen values come through {@link QuarryData}.
 */
public class SourceQuarryMenu extends AbstractContainerMenu {
    public static final int GLYPH_START = 0, VOID_START = 5, VOID_COUNT = 6, INV_START = 11, INV_END = 47;
    // Shared with SourceQuarryScreen and tools/gui_texture.py
    public static final int GLYPH_X = 130, GLYPH_Y = 31, VOID_X = 130, VOID_Y = 73;
    public static final int INV_X = 43, INV_Y = 140, HOTBAR_Y = 198;

    private final ContainerData data;
    private final ContainerLevelAccess access;
    private final @Nullable SourceQuarryBlockEntity be;
    public final BlockPos pos;
    /** Owner's name as sent when the screen opened (empty when unowned). */
    public final String ownerName;

    /** Client side. */
    public SourceQuarryMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, buf.readBlockPos(), buf.readUtf(), null, new QuarryGlyphs.Handler(() -> {}),
                new ItemStackHandler(VOID_COUNT), new SimpleContainerData(QuarryData.SLOTS));
    }

    /** Server side. */
    public SourceQuarryMenu(int id, Inventory inv, SourceQuarryBlockEntity be, ContainerData data) {
        this(id, inv, be.getBlockPos(), be.getOwnerName(), be, be.getGlyphs(), be.getVoidFilter(), data);
    }

    private SourceQuarryMenu(int id, Inventory inv, BlockPos pos, String ownerName, @Nullable SourceQuarryBlockEntity be,
                             IItemHandler glyphs, IItemHandler voids, ContainerData data) {
        super(ArsSourceQuarry.QUARRY_MENU.get(), id);
        this.pos = pos;
        this.ownerName = ownerName;
        this.be = be;
        this.data = data;
        this.access = ContainerLevelAccess.create(inv.player.level(), pos);

        for (int i = 0; i < QuarryGlyphs.COUNT; i++) {
            addSlot(new SlotItemHandler(glyphs, i, GLYPH_X + 1 + i * 18, GLYPH_Y + 1));
        }
        for (int i = 0; i < VOID_COUNT; i++) {
            addSlot(new GhostSlot(voids, i, VOID_X + 1 + i * 18, VOID_Y + 1));
        }
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                addSlot(new Slot(inv, c + r * 9 + 9, INV_X + 1 + c * 18, INV_Y + 1 + r * 18));
            }
        }
        for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c, INV_X + 1 + c * 18, HOTBAR_Y + 1));
        addDataSlots(data);
    }

    public int value(int index) {
        return QuarryData.get(data, index);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (be != null && player instanceof ServerPlayer sp) be.handleButton(id, sp);
        return true;
    }

    @Override
    public void clicked(int slotId, int button, ClickType type, Player player) {
        if (be != null && !be.canAccess(player)) return;
        if (slotId >= VOID_START && slotId < VOID_START + VOID_COUNT) {
            // Ghost slot: set the filter to a copy of the carried item, or clear it with an empty hand.
            if (type == ClickType.PICKUP || type == ClickType.QUICK_MOVE) {
                GhostSlot slot = (GhostSlot) slots.get(slotId);
                ItemStack carried = getCarried();
                slot.setFilter(carried.isEmpty() ? ItemStack.EMPTY : carried.copyWithCount(1));
            }
            return;
        }
        super.clicked(slotId, button, type, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem() || (index >= VOID_START && index < VOID_START + VOID_COUNT)) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < VOID_START) {
            if (!moveItemStackTo(stack, INV_START, INV_END, true)) return ItemStack.EMPTY;
        } else {
            // From the inventory: only glyphs move, into whichever glyph slot accepts them.
            if (!moveItemStackTo(stack, GLYPH_START, VOID_START, false)) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        if (be != null && !be.canAccess(player)) return false;   // closes if the owner locks it meanwhile
        return stillValid(access, player, ArsSourceQuarry.SOURCE_QUARRY.get());
    }

    /** Shows a filter item but can never be taken from or inserted into normally. */
    public static class GhostSlot extends SlotItemHandler {
        public GhostSlot(IItemHandler handler, int index, int x, int y) {
            super(handler, index, x, y);
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        void setFilter(ItemStack stack) {
            if (getItemHandler() instanceof ItemStackHandler h) h.setStackInSlot(getSlotIndex(), stack);
            setChanged();
        }
    }
}
