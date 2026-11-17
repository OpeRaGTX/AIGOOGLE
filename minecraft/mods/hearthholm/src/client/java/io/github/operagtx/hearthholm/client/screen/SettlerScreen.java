package io.github.operagtx.hearthholm.client.screen;

import io.github.operagtx.hearthholm.core.Profession;
import io.github.operagtx.hearthholm.core.Professions;
import io.github.operagtx.hearthholm.core.Stat;
import io.github.operagtx.hearthholm.core.Trait;
import io.github.operagtx.hearthholm.entity.MoodEntry;
import io.github.operagtx.hearthholm.network.Payloads;
import io.github.operagtx.hearthholm.network.Views;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** One settler: who they are, how they feel and why, their kit, and their profession. */
public final class SettlerScreen extends HhScreen {
    private Views.Settler view;
    private boolean confirmDismiss;

    public SettlerScreen(Views.Settler view) {
        super(Component.translatable("gui.hearthholm.settler"), 340, 232);
        this.view = view;
    }

    public static void show(Views.Settler view) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() instanceof SettlerScreen s && s.view.entityId() == view.entityId()) {
            s.view = view;
            s.rebuildWidgets();
        } else {
            mc.setScreenAndShow(new SettlerScreen(view));
        }
    }

    private void send(int action, String arg) {
        ClientPlayNetworking.send(new Payloads.SettlerAction(view.entityId(), action, arg));
    }

    @Override
    protected void init() {
        super.init();
        List<Profession> list = new ArrayList<>(Professions.REGISTRY.values());
        int cols = 5, rows = (list.size() + cols - 1) / cols;
        int x0 = left + 8, y0 = top + panelH - 24 - rows * 15;
        for (int i = 0; i < list.size(); i++) {
            Profession p = list.get(i);
            Component label = p.label();
            Button b = button(label, x0 + (i % cols) * 65, y0 + (i / cols) * 15, 63, 14, x -> send(Payloads.SettlerAction.SET_PROFESSION, p.id().toString()));
            b.active = !p.id().equals(view.profession());
            if (p != Professions.UNEMPLOYED) b.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                    Component.translatable("gui.hearthholm.aptitude", stars(p.aptitude(view.profile())))));
        }
        button(Component.translatable(confirmDismiss ? "gui.hearthholm.dismiss_confirm" : "gui.hearthholm.dismiss"),
                left + panelW - 110, top + panelH - 22, 102, 16, b -> {
                    if (confirmDismiss) {
                        send(Payloads.SettlerAction.DISMISS, "");
                        onClose();
                    } else {
                        confirmDismiss = true;
                        rebuildWidgets();
                    }
                });
        button(Component.translatable("gui.hearthholm.refresh"), left + 8, top + panelH - 22, 80, 16, b -> send(Payloads.SettlerAction.REFRESH, ""));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
        content(g, mx, my);
        super.extractRenderState(g, mx, my, pt);
    }

    private void content(GuiGraphicsExtractor g, int mx, int my) {
        int x = left + 8, y = top + 8;
        inset(g, x, y, 44, 44);
        face(g, view.profile().appearance(), view.profession(), x + 2, y + 2, 40);
        Profession p = Professions.get(view.profession());
        text(g, view.profile().fullName(), x + 52, y + 2, view.profile().legendary() ? 0xFFFFD35A : TITLE);
        text(g, p.label().copy().append(" · ").append(Component.translatable("gui.hearthholm.level", view.level())), x + 52, y + 14, 0xFF000000 | p.color());
        if (view.nextXp() > 0) {
            bar(g, x + 52, y + 25, 90, 5, view.xp() / (float) view.nextXp(), 0xFF7FC24A);
        }
        text(g, view.settlement(), x + 52, y + 34, DIM);
        g.textWithWordWrap(font, view.status(), x + 150, y + 4, 175, WARN);
        // closest friend and rival
        int rx = x + 150, ry = y + 36;
        if (view.friendValue() > 0 || view.rivalValue() < 0) {
            if (view.friendValue() > 0) {
                Component f = Component.literal("♥ ").append(view.friend()).append(" +" + view.friendValue());
                g.text(font, f, rx, ry, GOOD, false);
                if (inside(mx, my, rx, ry - 1, 80, 10)) g.setTooltipForNextFrame(font, Component.translatable("gui.hearthholm.friend"), mx, my);
                rx += font.width(f) + 8;
            }
            if (view.rivalValue() < 0) {
                Component f = Component.literal("⚔ ").append(view.rival()).append(" " + view.rivalValue());
                g.text(font, f, rx, ry, BAD, false);
                if (inside(mx, my, rx, ry - 1, 80, 10)) g.setTooltipForNextFrame(font, Component.translatable("gui.hearthholm.rival"), mx, my);
            }
        } else {
            text(g, Component.translatable("gui.hearthholm.no_relations"), rx, ry, DIM);
        }

        // stats
        int sy = top + 54;
        text(g, Component.translatable("gui.hearthholm.stats"), x, sy, TITLE);
        for (Stat st : Stat.values()) {
            sy += 11;
            text(g, st.label(), x, sy, DIM);
            bar(g, x + 70, sy + 1, 60, 6, view.profile().stat(st) / 10f, 0xFF8BB0D8);
            text(g, String.valueOf(view.profile().stat(st)), x + 134, sy, TEXT);
        }
        sy += 14;
        for (Trait t : view.profile().traitList()) {
            text(g, t.label(), x, sy, WARN);
            if (inside(mx, my, x, sy - 1, 140, 10)) g.setTooltipForNextFrame(font, t.description(), mx, my);
            sy += 10;
        }

        // needs + mood breakdown
        int nx = left + 160, ny = top + 54;
        text(g, Component.translatable("gui.hearthholm.food"), nx, ny, DIM);
        bar(g, nx + 50, ny + 1, 100, 6, view.food() / 20f, 0xFFD08A3A);
        ny += 12;
        text(g, Component.translatable("gui.hearthholm.mood"), nx, ny, DIM);
        bar(g, nx + 50, ny + 1, 100, 6, (view.mood() + 100) / 200f, moodColor(view.mood()));
        text(g, moodFace(view.mood()) + " " + view.mood(), nx + 154, ny, moodColor(view.mood()));
        ny += 12;
        if (!view.bed()) { text(g, Component.translatable("gui.hearthholm.no_bed"), nx, ny, BAD); ny += 11; }
        for (MoodEntry m : view.moods()) {
            if (ny > top + panelH - 98) break;
            text(g, m.label(), nx + 4, ny, TEXT);
            String v = (m.value() > 0 ? "+" : "") + m.value();
            text(g, v, nx + 150, ny, m.value() >= 0 ? GOOD : BAD);
            ny += 9;
        }

        // equipment + inventory
        int ix = left + 8, iy = top + panelH - 77;
        int i = 0;
        for (ItemStack st : view.equipment()) drawItem(g, st, ix + (i++) * 18, iy, mx, my);
        i = 0;
        for (ItemStack st : view.inventory()) {
            if (st.isEmpty()) continue;
            drawItem(g, st, ix + 100 + (i % 12) * 18, iy, mx, my);
            if (i >= 11) break;
            i++;
        }
    }

    private void drawItem(GuiGraphicsExtractor g, ItemStack st, int x, int y, int mx, int my) {
        g.fill(x, y, x + 17, y + 17, BG2);
        if (st.isEmpty()) return;
        g.item(st, x, y);
        g.itemDecorations(font, st, x, y);
        if (inside(mx, my, x, y, 17, 17)) g.setTooltipForNextFrame(font, st, mx, my);
    }
}
