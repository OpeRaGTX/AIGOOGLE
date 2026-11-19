package io.github.operagtx.hearthholm.client.screen;

import io.github.operagtx.hearthholm.Hearthholm;
import io.github.operagtx.hearthholm.menu.SettlerMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/** Wave J1: the inventory of a settler, laid out like a horse's: equipment slots with ghost icons, the pockets, the player's items. */
public final class SettlerInventoryScreen extends AbstractContainerScreen<SettlerMenu> {
    private static final Identifier BG = Hearthholm.id("textures/gui/settler_inventory.png");
    private static final String[] SLOT_KEYS = {"head", "chest", "legs", "feet", "mainhand", "offhand"};
    private static final int INK = 0xFF3B2A1A, DIM = 0xFF6E5A40;

    public SettlerInventoryScreen(SettlerMenu menu, Inventory inv, Component title) {
        super(menu, inv, title, SettlerMenu.WIDTH, SettlerMenu.HEIGHT);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float pt) {
        super.extractBackground(g, mx, my, pt);
        g.blit(RenderPipelines.GUI_TEXTURED, BG, leftPos, topPos, 0f, 0f, imageWidth, imageHeight, 256, 256);
        String t = title.getString();
        while (t.length() > 3 && font.width(t) > imageWidth - 16) t = t.substring(0, t.length() - 2).stripTrailing() + "…";
        g.text(font, t, leftPos + 8, topPos + 7, INK, false);
        g.text(font, Component.translatable("gui.hearthholm.inv_pockets"), leftPos + 8, topPos + 40, DIM, false);
        g.text(font, playerInventoryTitle, leftPos + 8, topPos + 100, DIM, false);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mx, int my) {
        // labels are drawn with the background (absolute coordinates)
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
        super.extractRenderState(g, mx, my, pt);
        if (hoveredSlot != null && !hoveredSlot.hasItem() && menu.getCarried().isEmpty()) {
            int i = hoveredSlot.index - SettlerMenu.EQUIP_START;
            if (i >= 0 && i < SLOT_KEYS.length) g.setTooltipForNextFrame(font, Component.translatable("gui.hearthholm.slot." + SLOT_KEYS[i]), mx, my);
        }
    }
}
