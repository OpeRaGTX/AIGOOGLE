package io.github.operagtx.hearthholm.client.screen;

import io.github.operagtx.hearthholm.client.render.SettlerTextures;
import io.github.operagtx.hearthholm.core.Profession;
import io.github.operagtx.hearthholm.core.Professions;
import io.github.operagtx.hearthholm.settlement.SettlerProfile;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Shared look of Hearthholm screens: a dark timber panel with brass trim. */
public abstract class HhScreen extends Screen {
    public static final int BG = 0xF21E1711, BG2 = 0xFF2B2119, TRIM = 0xFF9C7A45, TRIM_DARK = 0xFF5C4527;
    public static final int TITLE = 0xFFF2D49B, TEXT = 0xFFE8DCC4, DIM = 0xFFA6977E, GOOD = 0xFF8FD46B, BAD = 0xFFE36B5B, WARN = 0xFFE8B84A;

    protected int left, top;
    protected final int panelW, panelH;

    protected HhScreen(Component title, int w, int h) {
        super(title);
        this.panelW = w;
        this.panelH = h;
    }

    @Override
    protected void init() {
        left = (width - panelW) / 2;
        top = (height - panelH) / 2;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float pt) {
        super.extractBackground(g, mx, my, pt);
        panel(g, left, top, panelW, panelH);
    }

    protected static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x - 2, y - 2, x + w + 2, y + h + 2, TRIM_DARK);
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, TRIM);
        g.fill(x, y, x + w, y + h, BG);
    }

    protected static void inset(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, BG2);
        g.outline(x, y, w, h, TRIM_DARK);
    }

    protected static void bar(GuiGraphicsExtractor g, int x, int y, int w, int h, float frac, int color) {
        g.fill(x, y, x + w, y + h, 0xFF120D09);
        int fw = Math.round(Math.max(0, Math.min(1, frac)) * (w - 2));
        if (fw > 0) g.fill(x + 1, y + 1, x + 1 + fw, y + h - 1, color);
    }

    protected void text(GuiGraphicsExtractor g, Component c, int x, int y, int color) {
        g.text(font, c, x, y, color, false);
    }

    protected void text(GuiGraphicsExtractor g, String s, int x, int y, int color) {
        g.text(font, s, x, y, color, false);
    }

    /** Text cut to {@code maxW} pixels with an ellipsis. */
    protected String fit(String s, int maxW) {
        if (font.width(s) <= maxW) return s;
        return font.plainSubstrByWidth(s, Math.max(0, maxW - font.width("…"))) + "…";
    }

    /** The component itself when it fits (keeps its translation key), else a cut copy with an ellipsis. */
    protected Component fitLabel(Component c, int maxW) {
        return font.width(c.getString()) <= maxW ? c : Component.literal(fit(c.getString(), maxW));
    }

    protected String fit(Component c, int maxW) {
        return fit(c.getString(), maxW);
    }

    /** Draws truncated text; shows the full text as a tooltip when the mouse is over it and it was cut. */
    protected void fitText(GuiGraphicsExtractor g, Component c, int x, int y, int maxW, int color, int mx, int my) {
        String shown = fit(c, maxW);
        g.text(font, shown, x, y, color, false);
        if (!shown.equals(c.getString()) && inside(mx, my, x, y - 1, maxW, 10)) g.setTooltipForNextFrame(font, c, mx, my);
    }

    /** Settler face from the composed skin (front of head + hat layer). */
    protected static void face(GuiGraphicsExtractor g, int look, Identifier profession, int x, int y, int size) {
        Identifier tex = SettlerTextures.get(look, profession);
        g.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, 8, 8, size, size, 8, 8, 64, 64);
        g.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, 40, 8, size, size, 8, 8, 64, 64);
    }

    protected static String stars(int aptitude) {
        int n = Math.max(1, Math.min(5, (aptitude + 1) / 2));
        return "★".repeat(n) + "☆".repeat(5 - n);
    }

    protected static List<Profession> bestFor(SettlerProfile p, int count) {
        List<Profession> list = new ArrayList<>();
        for (Profession pr : Professions.REGISTRY.values()) if (pr != Professions.UNEMPLOYED) list.add(pr);
        list.sort(Comparator.comparingInt((Profession pr) -> -pr.aptitude(p)));
        return list.subList(0, Math.min(count, list.size()));
    }

    protected static int moodColor(int mood) {
        return mood >= 20 ? GOOD : mood >= -20 ? WARN : BAD;
    }

    protected static String moodFace(int mood) {
        return mood >= 20 ? "☺" : mood >= -20 ? "•" : "☹";
    }

    protected Button button(Component label, int x, int y, int w, int h, Button.OnPress press) {
        return addRenderableWidget(Button.builder(label, press).bounds(x, y, w, h).build());
    }

    protected boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }
}
