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
        super(Component.translatable("gui.hearthholm.work_site"), 300, 230);
        this.view = view;
        this.type = WorkSiteTypes.get(view.type());
    }

    public static void show(Views.WorkSite view) {
        Minecraft mc = Minecraft.getInstance();
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
                button(o.valueLabel(value), left + 150, y, 142, 16, b -> send(o.key(), o.next(value, 1)));
            } else {
                button(Component.literal("<"), left + 150, y, 16, 16, b -> send(o.key(), o.next(value, -1)));
                button(Component.literal(">"), left + 276, y, 16, 16, b -> send(o.key(), o.next(value, 1)));
            }
            y += 20;
        }
        if (view.total() > 0) {
            button(Component.translatable("gui.hearthholm.reset_progress"), left + 8, top + panelH - 22, 150, 16,
                    b -> send(Payloads.WorkSiteAction.RESET, 0));
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
        text(g, Component.translatable("gui.hearthholm.worker").append(": ").append(view.worker()), x, y, TEXT);
        text(g, view.status(), x, y + 10, WARN);
        if (view.total() > 0) {
            bar(g, x, y + 21, panelW - 16, 5, view.progress() / (float) view.total(), 0xFF7FC24A);
        }
        int oy = optionsTop();
        for (SiteOption o : type.options()) {
            int value = view.values().getOrDefault(o.key(), o.def());
            text(g, o.label(), x, oy + 4, TEXT);
            if (o.kind() != SiteOption.Kind.TOGGLE) {
                Component v = o.valueLabel(value);
                boolean locked = o.key().equals("blueprint") && lockedTier(value) >= 0;
                g.text(font, v, left + 221 - font.width(v) / 2, oy + 4, locked ? DIM : TITLE, false);
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
                for (Map.Entry<Item, Integer> e : bp.materials().entrySet()) {
                    int ix = x + (i % 14) * 20, iy = oy + 16 + (i / 14) * 20;
                    ItemStack st = new ItemStack(e.getKey(), Math.min(99, e.getValue()));
                    g.item(st, ix, iy);
                    g.itemDecorations(font, st, ix, iy, String.valueOf(e.getValue()));
                    if (inside(mx, my, ix, iy, 16, 16)) g.setTooltipForNextFrame(font, Component.empty().append(st.getHoverName()).append(" ×" + e.getValue()), mx, my);
                    i++;
                }
            }
        }
    }
}
