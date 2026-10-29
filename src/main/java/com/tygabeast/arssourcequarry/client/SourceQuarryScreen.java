package com.tygabeast.arssourcequarry.client;

import com.tygabeast.arssourcequarry.ArsSourceQuarry;
import com.tygabeast.arssourcequarry.block.QuarryData;
import com.tygabeast.arssourcequarry.block.QuarryGlyphs;
import com.tygabeast.arssourcequarry.block.SourceQuarryBlockEntity;
import com.tygabeast.arssourcequarry.menu.SourceQuarryMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * Ars Nouveau-style screen for the Source Quarry. Pure client rendering of values the menu syncs;
 * every button goes to the server as a menu button id and is validated there.
 */
public class SourceQuarryScreen extends AbstractContainerScreen<SourceQuarryMenu> {
    private static final ResourceLocation TEX = ArsSourceQuarry.id("textures/gui/source_quarry.png");
    private static final int INK = 0x4A3520, INK_SOFT = 0x8A6A44, ON_BUTTON = 0xFFF0D2, PENDING = 0x8A3AB0;

    // Layout (relative to the screen's top-left), shared with tools/gui_texture.py
    private static final int UP_MINUS_X = 40, UP_PLUS_X = 80, UP_Y = 52, DOWN_Y = 65, BEDROCK_X = 96;
    private static final int APPLY_X = 10, APPLY_Y = 80, APPLY_W = 100, APPLY_H = 14;
    private static final int LOCK_X = 176, REDSTONE_X = 198, OUTLINE_X = 220, ICON_Y = 103;
    private static final int VOID_MODE_X = 214, VOID_TAGS_X = 228, VOID_BTN_Y = 60;
    private static final int BAR_X = 10, PROGRESS_Y = 112, SOURCE_Y = 121, BAR_W = 130, BAR_H = 5;

    public SourceQuarryScreen(SourceQuarryMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = 248;
        imageHeight = 222;
        titleLabelX = 10;
        titleLabelY = 8;
        inventoryLabelY = 10000;   // no "Inventory" label, like Ars Nouveau's own screens
    }

    private int v(int index) {
        return menu.value(index);
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        renderOwnTooltips(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        g.blit(TEX, x, y, 0, 0, imageWidth, imageHeight);

        // Faded hints in empty glyph slots
        for (int i = 0; i < QuarryGlyphs.COUNT; i++) {
            if (!menu.getSlot(SourceQuarryMenu.GLYPH_START + i).hasItem()) {
                int sx = x + SourceQuarryMenu.GLYPH_X + 1 + i * 18, sy = y + SourceQuarryMenu.GLYPH_Y + 1;
                g.renderFakeItem(new ItemStack(QuarryGlyphs.item(i)), sx, sy);
                g.pose().pushPose();
                g.pose().translate(0, 0, 200);
                g.fill(sx, sy, sx + 16, sy + 16, 0xB8D6C49C);
                g.pose().popPose();
            }
        }

        // Up / Down buttons, bedrock shortcut, apply
        smallButton(g, x + UP_MINUS_X, y + UP_Y, mouseX, mouseY, true);
        smallButton(g, x + UP_PLUS_X, y + UP_Y, mouseX, mouseY, true);
        smallButton(g, x + UP_MINUS_X, y + DOWN_Y, mouseX, mouseY, true);
        smallButton(g, x + UP_PLUS_X, y + DOWN_Y, mouseX, mouseY, true);
        smallButton(g, x + BEDROCK_X, y + DOWN_Y, mouseX, mouseY, true);
        g.blit(TEX, x + BEDROCK_X + 2, y + DOWN_Y + 2, 190, 240, 8, 8);
        boolean canApply = v(QuarryData.APPLY) != QuarryData.APPLY_NONE;
        int au = !canApply ? 142 : in(mouseX, mouseY, x + APPLY_X, y + APPLY_Y, APPLY_W, APPLY_H) ? 40 : 40;
        int av = !canApply ? 224 : in(mouseX, mouseY, x + APPLY_X, y + APPLY_Y, APPLY_W, APPLY_H) ? 240 : 224;
        g.blit(TEX, x + APPLY_X, y + APPLY_Y, au, av, APPLY_W, APPLY_H);

        // Bars
        int progress = v(QuarryData.PROGRESS);
        int pw = (int) (BAR_W * Math.min(1000, Math.max(0, progress)) / 1000L);
        if (pw > 0) g.blit(TEX, x + BAR_X, y + PROGRESS_Y, pw, BAR_H, 180, 240, 1, BAR_H, 256, 256);
        int cap = Math.max(1, v(QuarryData.CAPACITY));
        int sw = (int) (BAR_W * Math.min(cap, Math.max(0, v(QuarryData.BUFFER))) / (long) cap);
        if (sw > 0) g.blit(TEX, x + BAR_X, y + SOURCE_Y, sw, BAR_H, 184, 240, 1, BAR_H, 256, 256);

        // Void filter mode buttons
        smallButton(g, x + VOID_MODE_X, y + VOID_BTN_Y, mouseX, mouseY, true);
        smallButton(g, x + VOID_TAGS_X, y + VOID_BTN_Y, mouseX, mouseY, true);

        // Icon buttons: lock, redstone mode and outline
        iconButton(g, x + LOCK_X, y + ICON_Y, mouseX, mouseY,
                new ItemStack(v(QuarryData.LOCKED) != 0 ? Items.IRON_DOOR : Items.OAK_DOOR));
        iconButton(g, x + REDSTONE_X, y + ICON_Y, mouseX, mouseY, redstoneIcon(v(QuarryData.REDSTONE)));
        iconButton(g, x + OUTLINE_X, y + ICON_Y, mouseX, mouseY,
                new ItemStack(v(QuarryData.OUTLINE) != 0 ? Items.ENDER_EYE : Items.ENDER_PEARL));
    }

    private void smallButton(GuiGraphics g, int bx, int by, int mx, int my, boolean enabled) {
        int u = !enabled ? 24 : in(mx, my, bx, by, 12, 12) ? 12 : 0;
        g.blit(TEX, bx, by, u, 224, 12, 12);
    }

    private void iconButton(GuiGraphics g, int bx, int by, int mx, int my, ItemStack icon) {
        g.blit(TEX, bx, by, in(mx, my, bx, by, 18, 18) ? 160 : 142, 238, 18, 18);
        g.renderItem(icon, bx + 1, by + 1);
    }

    private static ItemStack redstoneIcon(int mode) {
        return new ItemStack(switch (mode) {
            case 1 -> Items.REDSTONE_TORCH;
            case 2 -> Items.LEVER;
            case 3 -> Items.BARRIER;
            default -> Items.GUNPOWDER;
        });
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, titleLabelX, titleLabelY, INK, false);

        boolean linked = v(QuarryData.LINKED) != 0;
        if (linked) {
            g.drawString(font, "A  " + v(QuarryData.AX) + ", " + v(QuarryData.AY) + ", " + v(QuarryData.AZ), 10, 23, INK, false);
            g.drawString(font, "B  " + v(QuarryData.BX) + ", " + v(QuarryData.BY) + ", " + v(QuarryData.BZ), 10, 32, INK, false);
        } else {
            g.drawString(font, Component.translatable("gui.ars_source_quarry.no_markers"), 10, 23, INK_SOFT, false);
        }
        int up = v(QuarryData.UP), down = v(QuarryData.DOWN), base = v(QuarryData.BASE_Y);
        int lo = base - down, hi = base + up;
        boolean pendingChange = v(QuarryData.APPLY) != QuarryData.APPLY_NONE && linked;
        g.drawString(font, Component.translatable("gui.ars_source_quarry.range", lo, hi, hi - lo + 1), 10, 41,
                pendingChange ? PENDING : INK, false);

        g.drawString(font, Component.translatable("gui.ars_source_quarry.up"), 10, UP_Y + 2, INK, false);
        g.drawString(font, Component.translatable("gui.ars_source_quarry.down"), 10, DOWN_Y + 2, INK, false);
        centered(g, "-", UP_MINUS_X + 6, UP_Y + 2, ON_BUTTON);
        centered(g, "+", UP_PLUS_X + 6, UP_Y + 2, ON_BUTTON);
        centered(g, "-", UP_MINUS_X + 6, DOWN_Y + 2, ON_BUTTON);
        centered(g, "+", UP_PLUS_X + 6, DOWN_Y + 2, ON_BUTTON);
        centered(g, String.valueOf(up), (UP_MINUS_X + 12 + UP_PLUS_X) / 2, UP_Y + 2, INK);
        centered(g, String.valueOf(down), (UP_MINUS_X + 12 + UP_PLUS_X) / 2, DOWN_Y + 2, INK);

        int kind = v(QuarryData.APPLY);
        Component apply = Component.translatable(kind == QuarryData.APPLY_EXTEND ? "gui.ars_source_quarry.apply_extend"
                : linked ? "gui.ars_source_quarry.apply_restart" : "gui.ars_source_quarry.link");
        g.drawString(font, apply, APPLY_X + (APPLY_W - font.width(apply)) / 2, APPLY_Y + 3,
                kind == QuarryData.APPLY_NONE ? 0xD8D0C8 : ON_BUTTON, false);

        g.drawString(font, Component.translatable("gui.ars_source_quarry.glyphs"), SourceQuarryMenu.GLYPH_X, 22, INK, false);
        g.drawString(font, Component.translatable("gui.ars_source_quarry.cost", v(QuarryData.COST)),
                SourceQuarryMenu.GLYPH_X, 52, INK, false);
        g.drawString(font, Component.translatable("gui.ars_source_quarry.void"), SourceQuarryMenu.VOID_X, 64, INK, false);
        centered(g, v(QuarryData.VOID_WHITELIST) != 0 ? "K" : "D", VOID_MODE_X + 6, VOID_BTN_Y + 2, ON_BUTTON);
        centered(g, v(QuarryData.VOID_TAGS) != 0 ? "#" : "=", VOID_TAGS_X + 6, VOID_BTN_Y + 2, ON_BUTTON);

        SourceQuarryBlockEntity.Status[] statuses = SourceQuarryBlockEntity.Status.values();
        int s = v(QuarryData.STATUS);
        SourceQuarryBlockEntity.Status status = s >= 0 && s < statuses.length ? statuses[s] : SourceQuarryBlockEntity.Status.NOT_LINKED;
        Component line = status == SourceQuarryBlockEntity.Status.RUNNING
                ? Component.translatable("gui.ars_source_quarry.mining_layer", v(QuarryData.LAYER))
                : Component.translatable(status.key());
        g.drawString(font, line, 10, 100, INK, false);
        g.drawString(font, (v(QuarryData.PROGRESS) / 10) + "%", BAR_X + BAR_W + 5, PROGRESS_Y - 2, INK, false);
        g.drawString(font, String.valueOf(v(QuarryData.BUFFER)), BAR_X + BAR_W + 5, SOURCE_Y - 1, INK, false);
    }

    private void centered(GuiGraphics g, String text, int cx, int y, int color) {
        g.drawString(font, text, cx - font.width(text) / 2, y, color, false);
    }

    private void renderOwnTooltips(GuiGraphics g, int mx, int my) {
        int x = leftPos, y = topPos;
        List<Component> tip = new ArrayList<>();
        if (in(mx, my, x + BAR_X, y + PROGRESS_Y - 1, BAR_W, BAR_H + 2)) {
            tip.add(Component.translatable("gui.ars_source_quarry.tip.progress", v(QuarryData.PROGRESS) / 10, v(QuarryData.MINED)));
            int skipped = v(QuarryData.SKIPPED);
            if (skipped > 0) tip.add(Component.translatable("gui.ars_source_quarry.tip.skipped", skipped).withStyle(ChatFormatting.GRAY));
        } else if (in(mx, my, x + BAR_X, y + SOURCE_Y - 1, BAR_W, BAR_H + 2)) {
            tip.add(Component.translatable("gui.ars_source_quarry.tip.source", v(QuarryData.BUFFER), v(QuarryData.CAPACITY)));
            int est = v(QuarryData.ESTIMATE);
            if (est > 0) tip.add(Component.translatable("gui.ars_source_quarry.tip.estimate", est).withStyle(ChatFormatting.GRAY));
            if (v(QuarryData.CHUNK_LOADED) != 0) tip.add(Component.translatable("gui.ars_source_quarry.tip.chunk_loaded").withStyle(ChatFormatting.GRAY));
        } else if (in(mx, my, x + LOCK_X, y + ICON_Y, 18, 18)) {
            boolean locked = v(QuarryData.LOCKED) != 0;
            tip.add(Component.translatable(locked ? "gui.ars_source_quarry.tip.locked" : "gui.ars_source_quarry.tip.unlocked"));
            if (!menu.ownerName.isEmpty()) tip.add(Component.translatable("gui.ars_source_quarry.tip.owner", menu.ownerName).withStyle(ChatFormatting.GRAY));
            tip.add(Component.translatable(v(QuarryData.CAN_LOCK) != 0 ? "gui.ars_source_quarry.tip.lock_toggle"
                    : "gui.ars_source_quarry.tip.lock_owner_only").withStyle(ChatFormatting.GRAY));
        } else if (in(mx, my, x + VOID_MODE_X, y + VOID_BTN_Y, 12, 12)) {
            tip.add(Component.translatable(v(QuarryData.VOID_WHITELIST) != 0 ? "gui.ars_source_quarry.tip.whitelist"
                    : "gui.ars_source_quarry.tip.blacklist"));
        } else if (in(mx, my, x + VOID_TAGS_X, y + VOID_BTN_Y, 12, 12)) {
            tip.add(Component.translatable(v(QuarryData.VOID_TAGS) != 0 ? "gui.ars_source_quarry.tip.tags_on"
                    : "gui.ars_source_quarry.tip.tags_off"));
        } else if (in(mx, my, x + REDSTONE_X, y + ICON_Y, 18, 18)) {
            int m = v(QuarryData.REDSTONE);
            SourceQuarryBlockEntity.RedstoneMode[] modes = SourceQuarryBlockEntity.RedstoneMode.values();
            tip.add(Component.translatable(modes[Math.max(0, Math.min(3, m))].key()));
            tip.add(Component.translatable("gui.ars_source_quarry.tip.cycle").withStyle(ChatFormatting.GRAY));
        } else if (in(mx, my, x + OUTLINE_X, y + ICON_Y, 18, 18)) {
            tip.add(Component.translatable(v(QuarryData.OUTLINE) != 0 ? "gui.ars_source_quarry.tip.outline_on"
                    : "gui.ars_source_quarry.tip.outline_off"));
        } else if (in(mx, my, x + BEDROCK_X, y + DOWN_Y, 12, 12)) {
            tip.add(Component.translatable("gui.ars_source_quarry.tip.bedrock"));
        } else if (in(mx, my, x + UP_MINUS_X, y + UP_Y, 52, 25)) {
            tip.add(Component.translatable("gui.ars_source_quarry.tip.step").withStyle(ChatFormatting.GRAY));
        } else if (in(mx, my, x + APPLY_X, y + APPLY_Y, APPLY_W, APPLY_H)) {
            int kind = v(QuarryData.APPLY);
            tip.add(Component.translatable(kind == QuarryData.APPLY_EXTEND ? "gui.ars_source_quarry.tip.extend"
                    : kind == QuarryData.APPLY_NONE ? "gui.ars_source_quarry.tip.no_change"
                    : "gui.ars_source_quarry.tip.restart"));
        } else if (in(mx, my, x + SourceQuarryMenu.GLYPH_X, y + 50, 108, 10)) {
            tip.add(Component.translatable("gui.ars_source_quarry.tip.cost"));
        } else {
            for (int i = 0; i < QuarryGlyphs.COUNT; i++) {
                if (in(mx, my, x + SourceQuarryMenu.GLYPH_X + i * 18, y + SourceQuarryMenu.GLYPH_Y, 18, 18)
                        && !menu.getSlot(SourceQuarryMenu.GLYPH_START + i).hasItem()) {
                    tip.add(Component.translatable("gui.ars_source_quarry.glyph." + i));
                    tip.add(Component.translatable("gui.ars_source_quarry.glyph." + i + ".desc").withStyle(ChatFormatting.GRAY));
                }
            }
            for (int i = 0; i < SourceQuarryMenu.VOID_COUNT; i++) {
                if (in(mx, my, x + SourceQuarryMenu.VOID_X + i * 18, y + SourceQuarryMenu.VOID_Y, 18, 18)
                        && !menu.getSlot(SourceQuarryMenu.VOID_START + i).hasItem() && menu.getCarried().isEmpty()) {
                    tip.add(Component.translatable("gui.ars_source_quarry.tip.void"));
                }
            }
        }
        if (!tip.isEmpty()) g.renderComponentTooltip(font, tip, mx, my);
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = leftPos, y = topPos;
        boolean shift = Screen.hasShiftDown();
        int id = -1;
        if (button == 0 || button == 1) {
            if (in(mouseX, mouseY, x + UP_MINUS_X, y + UP_Y, 12, 12)) id = shift ? QuarryData.B_UP_MINUS10 : QuarryData.B_UP_MINUS;
            else if (in(mouseX, mouseY, x + UP_PLUS_X, y + UP_Y, 12, 12)) id = shift ? QuarryData.B_UP_PLUS10 : QuarryData.B_UP_PLUS;
            else if (in(mouseX, mouseY, x + UP_MINUS_X, y + DOWN_Y, 12, 12)) id = shift ? QuarryData.B_DOWN_MINUS10 : QuarryData.B_DOWN_MINUS;
            else if (in(mouseX, mouseY, x + UP_PLUS_X, y + DOWN_Y, 12, 12)) id = shift ? QuarryData.B_DOWN_PLUS10 : QuarryData.B_DOWN_PLUS;
            else if (in(mouseX, mouseY, x + BEDROCK_X, y + DOWN_Y, 12, 12)) id = QuarryData.B_BEDROCK;
            else if (in(mouseX, mouseY, x + APPLY_X, y + APPLY_Y, APPLY_W, APPLY_H)
                    && v(QuarryData.APPLY) != QuarryData.APPLY_NONE) id = QuarryData.B_APPLY;
            else if (in(mouseX, mouseY, x + REDSTONE_X, y + ICON_Y, 18, 18))
                id = button == 1 ? QuarryData.B_REDSTONE_BACK : QuarryData.B_REDSTONE;
            else if (in(mouseX, mouseY, x + OUTLINE_X, y + ICON_Y, 18, 18)) id = QuarryData.B_OUTLINE;
            else if (in(mouseX, mouseY, x + LOCK_X, y + ICON_Y, 18, 18) && v(QuarryData.CAN_LOCK) != 0) id = QuarryData.B_LOCK;
            else if (in(mouseX, mouseY, x + VOID_MODE_X, y + VOID_BTN_Y, 12, 12)) id = QuarryData.B_VOID_MODE;
            else if (in(mouseX, mouseY, x + VOID_TAGS_X, y + VOID_BTN_Y, 12, 12)) id = QuarryData.B_VOID_TAGS;
        }
        if (id >= 0 && minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /**
     * The area as currently set in the screen (including unapplied Up/Down), for the outline preview.
     * Like the quarry itself, it covers only the inside of the marker boundary.
     */
    public int[] previewBox() {
        if (v(QuarryData.LINKED) == 0) return null;
        int base = v(QuarryData.BASE_Y);
        return new int[]{Math.min(v(QuarryData.AX), v(QuarryData.BX)) + 1, base - v(QuarryData.DOWN),
                Math.min(v(QuarryData.AZ), v(QuarryData.BZ)) + 1, Math.max(v(QuarryData.AX), v(QuarryData.BX)) - 1,
                base + v(QuarryData.UP), Math.max(v(QuarryData.AZ), v(QuarryData.BZ)) - 1};
    }
}
