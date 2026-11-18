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
        super(Component.translatable("gui.hearthholm.settler"), 384, 230);
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
        list.removeIf(Profession::promotionOnly);
        int cols = 6, rows = (list.size() + cols - 1) / cols;
        int x0 = left + 8, y0 = top + panelH - 24 - rows * 15;
        for (int i = 0; i < list.size(); i++) {
            Profession p = list.get(i);
            Component label = Component.literal(fit(p.label(), 52));
            Button b = button(label, x0 + (i % cols) * 61, y0 + (i / cols) * 15, 59, 14, x -> send(Payloads.SettlerAction.SET_PROFESSION, p.id().toString()));
            b.active = !p.id().equals(view.profession());
            if (p != Professions.UNEMPLOYED) b.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                    p.label().copy().append(": ").append(Component.translatable("gui.hearthholm.aptitude", stars(p.aptitude(view.profile()))))));
        }
        button(Component.literal(fit(Component.translatable(confirmDismiss ? "gui.hearthholm.dismiss_confirm" : "gui.hearthholm.dismiss"), 94)),
                left + panelW - 110, top + panelH - 22, 102, 16, b -> {
                    if (confirmDismiss) {
                        send(Payloads.SettlerAction.DISMISS, "");
                        onClose();
                    } else {
                        confirmDismiss = true;
                        rebuildWidgets();
                    }
                });
        button(Component.literal(fit(Component.translatable("gui.hearthholm.refresh"), 72)), left + 8, top + panelH - 22, 80, 16, b -> send(Payloads.SettlerAction.REFRESH, ""));
        if (view.promotable()) {
            Button promote = button(Component.literal(fit(Component.translatable("gui.hearthholm.promote"), 126)), left + 92, top + panelH - 22, 134, 16, b -> send(Payloads.SettlerAction.PROMOTE, ""));
            promote.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("gui.hearthholm.promote_tip")));
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
        content(g, mx, my);
        super.extractRenderState(g, mx, my, pt);
    }

    private void content(GuiGraphicsExtractor g, int mx, int my) {
        int x = left + 8, y = top + 8;
        int rightEdge = left + panelW - 8;
        inset(g, x, y, 44, 44);
        face(g, view.profile().appearance(), view.profession(), x + 2, y + 2, 40);
        Profession p = Professions.get(view.profession());
        fitText(g, view.profile().fullName(), x + 52, y + 2, rightEdge - x - 52, view.profile().legendary() ? 0xFFFFD35A : TITLE, mx, my);
        // row 2: profession + level on the left, status on the right (own columns, never overlapping)
        int colR = x + 190;
        fitText(g, p.label().copy().append(" · ").append(Component.translatable("gui.hearthholm.level", view.level())),
                x + 52, y + 13, colR - x - 52 - 6, 0xFF000000 | p.color(), mx, my);
        fitText(g, view.status(), colR, y + 13, rightEdge - colR, WARN, mx, my);
        if (view.nextXp() > 0) {
            bar(g, x + 52, y + 25, 90, 5, view.xp() / (float) view.nextXp(), 0xFF7FC24A);
            if (inside(mx, my, x + 52, y + 24, 90, 7)) g.setTooltipForNextFrame(font, Component.literal("XP " + view.xp() + " / " + view.nextXp()), mx, my);
        }
        fitText(g, view.settlement(), x + 52, y + 34, colR - x - 52 - 6, DIM, mx, my);
        // closest friend and rival
        int rx = colR, ry = y + 34;
        int room = rightEdge - colR;
        if (view.friendValue() > 0 || view.rivalValue() < 0) {
            int half = view.friendValue() > 0 && view.rivalValue() < 0 ? room / 2 - 4 : room;
            if (view.friendValue() > 0) {
                Component f = Component.literal("♥ ").append(view.friend()).append(" +" + view.friendValue());
                fitText(g, f, rx, ry, half, GOOD, mx, my);
                if (inside(mx, my, rx, ry - 1, half, 10)) g.setTooltipForNextFrame(font, Component.translatable("gui.hearthholm.friend"), mx, my);
                rx += half + 8;
            }
            if (view.rivalValue() < 0) {
                Component f = Component.literal("⚔ ").append(view.rival()).append(" " + view.rivalValue());
                fitText(g, f, rx, ry, rightEdge - rx, BAD, mx, my);
                if (inside(mx, my, rx, ry - 1, rightEdge - rx, 10)) g.setTooltipForNextFrame(font, Component.translatable("gui.hearthholm.rival"), mx, my);
            }
        } else {
            fitText(g, Component.translatable("gui.hearthholm.no_relations"), rx, ry, room, DIM, mx, my);
        }

        // stats (left column)
        int colW = 156;
        int sy = top + 54;
        text(g, Component.translatable("gui.hearthholm.stats"), x, sy, TITLE);
        for (Stat st : Stat.values()) {
            sy += 10;
            text(g, fit(st.label(), 70), x, sy, DIM);
            bar(g, x + 74, sy + 1, 56, 6, view.profile().stat(st) / 10f, 0xFF8BB0D8);
            text(g, String.valueOf(view.profile().stat(st)), x + 134, sy, TEXT);
        }
        // traits flow in rows below the stats
        sy += 14;
        int tx = x, rowsUsed = 0;
        for (Trait t : view.profile().traitList()) {
            String name = fit(t.label(), colW);
            int w = font.width(name);
            if (tx > x && tx + w > x + colW) { tx = x; sy += 10; rowsUsed++; }
            if (rowsUsed >= 2) break;
            text(g, name, tx, sy, WARN);
            if (inside(mx, my, tx, sy - 1, w, 10)) g.setTooltipForNextFrame(font, t.description(), mx, my);
            tx += w + 8;
        }

        // needs + mood breakdown (right column)
        int nx = x + colW + 14, ny = top + 54;
        int lw = 56, bx = nx + lw + 4, bw = 84, vx = bx + bw + 4;
        text(g, fit(Component.translatable("gui.hearthholm.food"), lw), nx, ny, DIM);
        bar(g, bx, ny + 1, bw, 6, view.food() / 20f, 0xFFD08A3A);
        text(g, view.food() + "/20", vx, ny, TEXT);
        ny += 11;
        text(g, fit(Component.translatable("gui.hearthholm.health"), lw), nx, ny, DIM);
        bar(g, bx, ny + 1, bw, 6, view.maxHp() <= 0 ? 0 : view.hp() / (float) view.maxHp(), 0xFFD4504A);
        text(g, "HP " + view.hp() + "/" + view.maxHp(), vx, ny, view.hp() * 3 < view.maxHp() ? BAD : TEXT);
        ny += 11;
        text(g, fit(Component.translatable("gui.hearthholm.mood"), lw), nx, ny, DIM);
        bar(g, bx, ny + 1, bw, 6, (view.mood() + 100) / 200f, moodColor(view.mood()));
        text(g, moodFace(view.mood()) + " " + view.mood(), vx, ny, moodColor(view.mood()));
        ny += 12;
        int listBottom = top + panelH - 94;
        int valX = rightEdge - 22;
        if (!view.bed()) { fitText(g, Component.translatable("gui.hearthholm.no_bed"), nx, ny, rightEdge - nx, BAD, mx, my); ny += 10; }
        for (MoodEntry m : view.moods().stream().sorted(java.util.Comparator.comparingInt((MoodEntry e) -> -Math.abs(e.value()))).toList()) {
            if (ny + 9 > listBottom) break;
            fitText(g, m.label(), nx + 4, ny, valX - nx - 8, TEXT, mx, my);
            String v = (m.value() > 0 ? "+" : "") + m.value();
            text(g, v, rightEdge - font.width(v), ny, m.value() >= 0 ? GOOD : BAD);
            ny += 9;
        }

        // equipment + inventory
        int ix = left + 8, iy = top + panelH - 89;
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
