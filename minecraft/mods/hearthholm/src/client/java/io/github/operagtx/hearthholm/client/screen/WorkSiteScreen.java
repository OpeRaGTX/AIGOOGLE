package io.github.operagtx.hearthholm.client.screen;

import io.github.operagtx.hearthholm.blueprint.Blueprint;
import io.github.operagtx.hearthholm.blueprint.BlueprintLibrary;
import io.github.operagtx.hearthholm.network.Payloads;
import io.github.operagtx.hearthholm.network.Views;
import io.github.operagtx.hearthholm.settlement.SettlementTier;
import io.github.operagtx.hearthholm.worksite.SiteOption;
import io.github.operagtx.hearthholm.worksite.WorkSiteType;
import io.github.operagtx.hearthholm.worksite.WorkSiteTypes;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Map;

/** Config screen generated from a work-site type's options. */
public final class WorkSiteScreen extends HhScreen {
    private Views.WorkSite view;
    private final WorkSiteType type;

    public WorkSiteScreen(Views.WorkSite view) {
        super(Component.translatable("gui.hearthholm.work_site"), 340, 230);
        this.view = view;
        this.type = WorkSiteTypes.get(view.type());
    }

    public static void show(Views.WorkSite view) {
        Minecraft mc = Minecraft.getInstance();
        if (ZoneMapScreen.updateView(view)) return;   // wave J2: option changes made on the map screen
        if (mc.gui.screen() instanceof WorkSiteScreen s && s.view.pos().equals(view.pos())) {
            s.view = view;
            s.rebuildWidgets();
        } else {
            mc.setScreenAndShow(new WorkSiteScreen(view));
        }
    }

    private void send(String key, int value) {
        ClientPlayNetworking.send(new Payloads.WorkSiteAction(view.pos(), key, value));
    }

    /** Tier index that unlocks blueprint {@code index} when the settlement has not reached it (and it is not bought), else -1. */
    private int lockedTier(int index) {
        Blueprint bp = BlueprintLibrary.byIndex(index);
        if (bp == null || !view.values().containsKey("_tier")) return -1;
        int need = BlueprintLibrary.requiredTier(bp.id());
        if (view.values().getOrDefault("_tier", 0) >= need || view.values().containsKey("_u" + index)) return -1;
        return need;
    }

    private int hintHeight() {
        return font.split(type.hint(), panelW - 16).size() * 9;
    }

    /** Wave M2: how many of the chosen blueprint's materials the storage does not hold in full (server numbers {@code _h<i>}, same order as the icons). */
    private int missingCount() {
        Blueprint bp = BlueprintLibrary.byIndex(view.values().getOrDefault("blueprint", 0));
        if (bp == null || lockedTier(view.values().getOrDefault("blueprint", 0)) >= 0) return 0;
        int n = 0, i = 0;
        for (Blueprint.Material m : bp.materialList()) if (view.values().getOrDefault("_h" + i++, 0) < m.count()) n++;
        return n;
    }

    /** A 7x7 check mark (enough in stock) or cross (missing), drawn from rectangles so it needs no font glyph. */
    static void mark(GuiGraphicsExtractor g, int x, int y, boolean ok) {
        int back = 0xE0101010, c = ok ? 0xFF6FE04A : 0xFFFF5A4A;
        g.fill(x - 1, y - 1, x + 8, y + 8, back);
        if (ok) {
            int[][] px = {{0, 4}, {1, 5}, {2, 6}, {3, 5}, {4, 4}, {5, 3}, {6, 2}};
            for (int[] p : px) { g.fill(x + p[0], y + p[1] - 1, x + p[0] + 1, y + p[1] + 1, c); }
        } else {
            for (int k = 0; k < 7; k++) { g.fill(x + k, y + k, x + k + 1, y + k + 1, c); g.fill(x + 6 - k, y + k, x + 7 - k, y + k + 1, c); }
        }
    }

    private int optionsTop() {
        return top + 22 + hintHeight() + 36;
    }

    @Override
    protected void init() {
        super.init();
        int y = optionsTop();
        for (SiteOption o : type.options()) {
            int value = view.values().getOrDefault(o.key(), o.def());
            if (o.kind() == SiteOption.Kind.TOGGLE) {
                button(fitLabel(o.valueLabel(value), 154), left + 170, y, 162, 16, b -> send(o.key(), o.next(value, 1)));
            } else {
                button(Component.literal("<"), left + 170, y, 16, 16, b -> send(o.key(), o.next(value, -1)));
                button(Component.literal(">"), left + 316, y, 16, 16, b -> send(o.key(), o.next(value, 1)));
            }
            y += 20;
        }
        if (view.total() > 0) {
            button(Component.translatable("gui.hearthholm.reset_progress"), left + 8, top + panelH - 22, 150, 16,
                    b -> send(Payloads.WorkSiteAction.RESET, 0));
        }
        if (type.id().equals(WorkSiteTypes.CONSTRUCTION_ID) && missingCount() > 0) {   // wave M2: the missing materials go to the workshops' orders
            button(fitLabel(Component.translatable("gui.hearthholm.order_missing"), 164), left + 162, top + panelH - 22, 170, 16,
                    b -> send(Payloads.WorkSiteAction.ORDER_MISSING, 0)).setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("gui.hearthholm.order_missing.tip")));
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
        content(g, mx, my);
        super.extractRenderState(g, mx, my, pt);
    }

    private void content(GuiGraphicsExtractor g, int mx, int my) {
        int x = left + 8, y = top + 8;
        text(g, type.label(), x, y, TITLE);
        y += 14;
        g.textWithWordWrap(font, type.hint(), x, y, panelW - 16, DIM);
        y += hintHeight() + 3;
        fitText(g, Component.translatable("gui.hearthholm.worker").append(": ").append(view.worker()), x, y, panelW - 16, TEXT, mx, my);
        fitText(g, view.status(), x, y + 10, panelW - 16, WARN, mx, my);
        if (view.total() > 0 && view.progress() > 0) {
            bar(g, x, y + 21, panelW - 16, 5, view.progress() / (float) view.total(), 0xFF7FC24A);
            if (inside(mx, my, x, y + 20, panelW - 16, 7)) g.setTooltipForNextFrame(font, Component.literal(view.progress() + " / " + view.total()), mx, my);
        }
        int oy = optionsTop();
        for (SiteOption o : type.options()) {
            int value = view.values().getOrDefault(o.key(), o.def());
            fitText(g, o.label(), x, oy + 4, 156, TEXT, mx, my);
            if (o.kind() != SiteOption.Kind.TOGGLE) {
                Component v = o.valueLabel(value);
                boolean locked = o.key().equals("blueprint") && lockedTier(value) >= 0;
                int zx = left + 190, zw = 122;   // between the arrows
                String shown = fit(v, zw);
                g.text(font, shown, zx + (zw - font.width(shown)) / 2, oy + 4, locked ? DIM : TITLE, false);
                if (!shown.equals(v.getString()) && inside(mx, my, zx, oy + 2, zw, 12)) g.setTooltipForNextFrame(font, v, mx, my);
            }
            oy += 20;
        }
        if (type.id().equals(WorkSiteTypes.CONSTRUCTION_ID)) {
            Blueprint bp = BlueprintLibrary.byIndex(view.values().getOrDefault("blueprint", 0));
            if (bp != null) {
                int lock = lockedTier(view.values().getOrDefault("blueprint", 0));
                if (lock >= 0) text(g, Component.translatable("gui.hearthholm.needs_tier", SettlementTier.TIERS.get(lock).label()), x, oy + 4, BAD);
                else text(g, Component.translatable("gui.hearthholm.materials", bp.width(), bp.depth(), bp.height()), x, oy + 4, DIM);
                int i = 0;
                for (Blueprint.Material m : bp.materialList()) {
                    int ix = x + (i % 16) * 20, iy = oy + 16 + (i / 16) * 20;
                    ItemStack st = new ItemStack(m.icon(), Math.min(99, m.count()));
                    int have = view.values().getOrDefault("_h" + i, 0);
                    g.item(st, ix, iy);
                    g.itemDecorations(font, st, ix, iy, String.valueOf(m.count()));
                    mark(g, ix + 9, iy - 3, have >= m.count());
                    if (inside(mx, my, ix, iy, 16, 16)) {
                        Component name = m.anyKey() != null ? Component.translatable("gui.hearthholm.any." + m.anyKey()) : st.getHoverName();
                        Component tip = Component.empty().append(name).append(" ×" + m.count()).append(Component.literal("  "))
                                .append(Component.translatable("gui.hearthholm.in_stock", have, m.count()).withStyle(have >= m.count() ? net.minecraft.ChatFormatting.GREEN : net.minecraft.ChatFormatting.RED));
                        if (m.anyKey() == null && Blueprint.isWoodVariant(m.icon())) tip = tip.copy().append(Component.translatable("gui.hearthholm.or_wood").withStyle(net.minecraft.ChatFormatting.GRAY));
                        g.setTooltipForNextFrame(font, tip, mx, my);
                    }
                    i++;
                }
            }
        }
    }
}
