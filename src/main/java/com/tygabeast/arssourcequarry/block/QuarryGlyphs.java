package com.tygabeast.arssourcequarry.block;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * The five glyph slots. Glyphs are looked up by registry id (ars_nouveau:glyph_*), so no Ars Nouveau
 * spell classes are touched. Lookups are resolved once and cached.
 */
public final class QuarryGlyphs {
    public static final int AMPLIFY = 0, EXTRACT = 1, FORTUNE = 2, SMELT = 3, CRUSH = 4, COUNT = 5;
    private static final String[] IDS = {"glyph_amplify", "glyph_extract", "glyph_fortune", "glyph_smelt", "glyph_crush"};
    private static final int[] MAX = {3, 1, 3, 1, 1};
    private static Item[] items;

    private QuarryGlyphs() {}

    public static Item item(int slot) {
        if (items == null) {
            Item[] arr = new Item[COUNT];
            for (int i = 0; i < COUNT; i++) {
                arr[i] = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("ars_nouveau", IDS[i]));
            }
            items = arr;
        }
        return items[slot];
    }

    public static int maxStack(int slot) {
        return MAX[slot];
    }

    /** Item handler for the glyph slots: each slot takes only its glyph; Extract and Fortune exclude each other. */
    public static class Handler extends ItemStackHandler {
        private final Runnable onChange;

        public Handler(Runnable onChange) {
            super(COUNT);
            this.onChange = onChange;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            Item want = item(slot);
            if (want == Items.AIR || !stack.is(want)) return false;
            if (slot == EXTRACT && !getStackInSlot(FORTUNE).isEmpty()) return false;
            if (slot == FORTUNE && !getStackInSlot(EXTRACT).isEmpty()) return false;
            return true;
        }

        @Override
        public int getSlotLimit(int slot) {
            return MAX[slot];
        }

        @Override
        protected void onContentsChanged(int slot) {
            onChange.run();
        }

        public int level(int slot) {
            return getStackInSlot(slot).getCount();
        }
    }
}
