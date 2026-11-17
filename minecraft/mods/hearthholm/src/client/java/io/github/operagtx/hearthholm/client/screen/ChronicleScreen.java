package io.github.operagtx.hearthholm.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Wave F: the Founder's Chronicle book. Nine chapters (list on the left), two pages each; arrows flip through the whole
 * book. Text lives in the language files as {@code guide.hearthholm.chN.title / p1 / p2}.
 */
public final class ChronicleScreen extends HhScreen {
    public static final int CHAPTERS = 9, PAGES_PER_CHAPTER = 2, LIST_W = 98;
    private static int lastPage;
    private int page;

    public ChronicleScreen(int page) {
        super(Component.translatable("gui.hearthholm.guide"), 340, 212);
        this.page = Math.max(0, Math.min(CHAPTERS * PAGES_PER_CHAPTER - 1, page));
    }

    public static void open() {
        Minecraft.getInstance().setScreenAndShow(new ChronicleScreen(lastPage));
    }

    public static void open(int chapter, int pageInChapter) {
        lastPage = chapter * PAGES_PER_CHAPTER + pageInChapter;
        open();
    }

    public int page() { return page; }

    private void go(int p) {
        page = Math.max(0, Math.min(CHAPTERS * PAGES_PER_CHAPTER - 1, p));
        lastPage = page;
        rebuildWidgets();
    }

    @Override
    protected void init() {
        super.init();
        int chapter = page / PAGES_PER_CHAPTER;
        for (int i = 0; i < CHAPTERS; i++) {
            int target = i * PAGES_PER_CHAPTER;
            var b = button(Component.literal((i + 1) + ". ").append(Component.translatable("guide.hearthholm.ch" + (i + 1) + ".title")),
                    left + 8, top + 26 + i * 17, LIST_W, 15, x -> go(target));
            b.active = i != chapter;
        }
        int ny = top + panelH - 24;
        var prev = button(Component.literal("<"), left + LIST_W + 40, ny, 24, 16, b -> go(page - 1));
        var next = button(Component.literal(">"), left + panelW - 32, ny, 24, 16, b -> go(page + 1));
        prev.active = page > 0;
        next.active = page < CHAPTERS * PAGES_PER_CHAPTER - 1;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
        content(g);
        super.extractRenderState(g, mx, my, pt);
    }

    private void content(GuiGraphicsExtractor g) {
        text(g, Component.translatable("gui.hearthholm.guide"), left + 8, top + 8, TITLE);
        int chapter = page / PAGES_PER_CHAPTER + 1, sub = page % PAGES_PER_CHAPTER + 1;
        int tx = left + LIST_W + 20, tw = panelW - LIST_W - 30;
        inset(g, tx - 6, top + 22, tw + 12, panelH - 52);
        g.fill(left + LIST_W + 14, top + 8, left + LIST_W + 15, top + panelH - 8, TRIM_DARK);
        Component title = Component.translatable("guide.hearthholm.ch" + chapter + ".title");
        text(g, title, tx, top + 28, TITLE);
        g.fill(tx, top + 39, tx + tw, top + 40, TRIM_DARK);
        g.textWithWordWrap(font, Component.translatable("guide.hearthholm.ch" + chapter + ".p" + sub), tx, top + 45, tw, TEXT);
        Component pageNo = Component.translatable("gui.hearthholm.guide_page", page + 1, CHAPTERS * PAGES_PER_CHAPTER);
        text(g, pageNo, left + LIST_W + 20 + (tw - font.width(pageNo)) / 2 + 2, top + panelH - 20, DIM);
    }
}
